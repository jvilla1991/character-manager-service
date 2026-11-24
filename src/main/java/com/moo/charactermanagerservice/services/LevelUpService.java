package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FeatureGain;
import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.SpellGrant;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassFeatures;
import com.moo.charactermanagerservice.progression.ClassProgression;
import com.moo.charactermanagerservice.progression.ClassSpellGrants;
import com.moo.charactermanagerservice.progression.FeatCatalog;
import com.moo.charactermanagerservice.progression.FeatSpellGrants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Server-authoritative level-up rules engine (single-class, D&D 5e 2024).
 *
 * <p><strong>Single responsibility:</strong> orchestrate the deterministic gains of advancing one
 * level — validate the player's choices, then apply them in the right order. It does not load,
 * persist, or authorize ({@link PCService} owns that and delegates here), and it delegates the
 * mechanical sub-tasks to collaborators: {@link HpCalculator} (HP-per-level + the RNG seam),
 * {@link SpellSlotState} (the {@code spellSlots} JSON), and {@link PcJsonColumns} (the
 * {@code features}/{@code spells} JSON columns). Keeping the rules out of the controller and out of
 * PCService means the math is unit tested in isolation (no repo, no Spring context, no security).
 *
 * <p><strong>HP policy:</strong> per request, either the fixed average or a roll ({@link HpMode}) —
 * see {@link HpCalculator}. The mode defaults to {@link HpMode#AVERAGE} so behaviour is unchanged
 * when none is sent. Because a roll isn't known until commit, the {@link #preview(PC) preview}
 * always reports the average (the floor/best-case estimate).
 *
 * <p><strong>Spell slots (Phase 2):</strong> the {@code spellSlots} column is rebuilt by
 * {@link SpellSlotState#rebuild} — {@code max} is the class table for the new level
 * ({@link ClassProgression#spellSlotsFor}: full casters, warlock, and the half casters Paladin and
 * Ranger) plus the DM's bonus lines, the same sum every sheet save uses, while the player's
 * {@code used} count is preserved (clamped to the new max). A character with no slots is left
 * untouched.
 *
 * <p><strong>Multiclass seam:</strong> today total level == single class level, so all tables key
 * off {@code pc.clazz} / {@code pc.level}. When multiclassing lands, hit-die/slot lookups move to
 * {@code (class, classLevel)} (slots via the multiclass caster-level rule) while proficiency bonus
 * stays on total level — only the inputs change, not this contract.
 */
@Service
public class LevelUpService {

    private final HpCalculator hpCalculator;
    private final PcJsonColumns jsonColumns;

    @Autowired
    public LevelUpService(ObjectMapper objectMapper) {
        this(objectMapper, new Random());
    }

    /** Test seam: inject a seeded/fixed RNG so rolled-HP outcomes are deterministic. */
    LevelUpService(ObjectMapper objectMapper, RandomGenerator rng) {
        this.hpCalculator = new HpCalculator(rng);
        this.jsonColumns = new PcJsonColumns(objectMapper);
    }

    /** Compute — without persisting — what advancing one level would grant. */
    public LevelUpPreview preview(PC pc) {
        int currentLevel = currentLevel(pc);
        assertCanLevelUp(currentLevel);

        int newLevel = currentLevel + 1;
        int hitDie = ClassProgression.hitDie(pc.getClazz());
        int conMod = ClassProgression.abilityModifier(nz(pc.getAbilityCon()));
        int hpGained = hpCalculator.average(hitDie, conMod);
        boolean asiDue = ClassProgression.isAsiLevel(pc.getClazz(), newLevel);
        List<String> featOptions = asiDue ? FeatCatalog.featOptions(candidate(pc, newLevel)) : List.of();
        boolean subclassDue = subclassDue(pc, newLevel);
        boolean fightingStyleDue = FeatCatalog.fightingStyleDue(pc.getClazz(), newLevel);
        List<String> fightingStyleOptions =
                FeatCatalog.fightingStyleOptions(pc.getClazz(), pc.getSubclass(), newLevel);
        List<String> recommendedFightingStyles = fightingStyleDue
                ? ClassProgression.recommendedFightingStyles(pc.getClazz(), pc.getSpecialization()).stream()
                        .filter(fightingStyleOptions::contains).toList()
                : List.of();

        return new LevelUpPreview(
                currentLevel,
                newLevel,
                hitDie,
                conMod,
                hpGained,
                nz(pc.getHpMax()) + hpGained,
                proficiencyBonus(pc, currentLevel),
                proficiencyBonus(pc, newLevel),
                SpellSlotState.storedMax(pc.getSpellSlots()),
                SpellSlotState.maxFor(pc.getClazz(), newLevel, pc.getStatAdjustments()),
                subclassDue,
                ClassProgression.subclassesFor(pc.getClazz(), pc.getSpecialization()),
                asiDue,
                featOptions,
                ClassFeatures.featuresAt(pc.getClazz(), pc.getSubclass(), pc.getSpecialization(), newLevel),
                ClassProgression.cantripsKnownFor(pc.getClazz(), currentLevel),
                ClassProgression.cantripsKnownFor(pc.getClazz(), newLevel),
                ClassProgression.preparedSpellsFor(pc.getClazz(), currentLevel),
                ClassProgression.preparedSpellsFor(pc.getClazz(), newLevel),
                fightingStyleDue,
                fightingStyleOptions,
                subclassDue ? ClassProgression.recommendedSubclass(pc.getClazz(), pc.getSpecialization()) : null,
                recommendedFightingStyles,
                grantsOwed(pc, pc.getSubclass(), newLevel),
                subclassDue ? grantsOwedBySubclass(pc, newLevel) : Map.of(),
                grantsOwedByFeat(pc, featOptions, newLevel)
        );
    }

    /** The character as the feat catalog sees it when it reaches {@code newLevel}. */
    private FeatCatalog.Candidate candidate(PC pc, int newLevel) {
        return new FeatCatalog.Candidate(pc.getClazz(), pc.getSubclass(), newLevel, abilityScores(pc),
                CharacterFeats.namesOn(pc));
    }

    /**
     * The spells a character already has, as the grant tables ask about them: a spell a class
     * feature grants is held once it is marked always prepared; a spell a feat grants also
     * counts as held when it is a cantrip the character knows (a known cantrip needs no marker).
     */
    private record HeldSpells(Set<String> alwaysPrepared, Set<String> cantrips) {
        boolean hasClassSpell(SpellGrant grant) {
            return alwaysPrepared.contains(grant.name().toLowerCase());
        }

        boolean hasFeatSpell(SpellGrant grant) {
            return hasClassSpell(grant) || cantrips.contains(grant.name().toLowerCase());
        }
    }

    private HeldSpells heldSpells(PC pc) {
        Set<String> alwaysPrepared = new HashSet<>();
        Set<String> cantrips = new HashSet<>();
        for (Map<String, Object> spell : jsonColumns.parse(pc.getSpells())) {
            String name = spellName(spell).toLowerCase();
            if (Boolean.TRUE.equals(spell.get("alwaysPrepared"))) alwaysPrepared.add(name);
            if (spellLevelOf(spell) == 0) cantrips.add(name);
        }
        return new HeldSpells(alwaysPrepared, cantrips);
    }

    /**
     * The always-prepared spells the character should have at {@code level} with the given
     * subclass and does not have marked yet — what this level-up hands over: the class and
     * subclass grants, then the spells of the feats it already has (a School Adept's row for a
     * spell slot level it has just reached, or a spell it was owed and never got).
     */
    private List<SpellGrant> grantsOwed(PC pc, String subclass, int level) {
        HeldSpells held = heldSpells(pc);
        List<SpellGrant> owed = new ArrayList<>(
                ClassSpellGrants.grantsThrough(pc.getClazz(), subclass, pc.getSpecialization(), level).stream()
                        .filter(g -> !held.hasClassSpell(g))
                        .toList());
        for (SpellGrant grant : FeatSpellGrants.grantsThrough(
                CharacterFeats.namesOn(pc), pc.getClazz(), subclass, level)) {
            if (!held.hasFeatSpell(grant) && !names(owed, grant)) owed.add(grant);
        }
        return owed;
    }

    /** True when the list already names this grant's spell. */
    private static boolean names(List<SpellGrant> grants, SpellGrant grant) {
        return grants.stream().anyMatch(g -> g.name().equalsIgnoreCase(grant.name()));
    }

    /**
     * For each feat on offer that gives spells of its own: the ones the character would be owed
     * at {@code level} on top of {@link #grantsOwed}. Feats that add nothing are left out.
     */
    private Map<String, List<SpellGrant>> grantsOwedByFeat(PC pc, List<String> featOptions, int level) {
        Map<String, List<SpellGrant>> byFeat = new LinkedHashMap<>();
        if (featOptions.isEmpty()) return byFeat;
        HeldSpells held = heldSpells(pc);
        List<SpellGrant> base = grantsOwed(pc, pc.getSubclass(), level);
        for (String option : featOptions) {
            List<SpellGrant> owed = FeatSpellGrants.grantsFor(option, pc.getClazz(), pc.getSubclass(), level)
                    .stream()
                    .filter(g -> !held.hasFeatSpell(g) && !names(base, g))
                    .toList();
            if (!owed.isEmpty()) byFeat.put(option, owed);
        }
        return byFeat;
    }

    /** {@link #grantsOwed} for each subclass on offer; options that add nothing are left out. */
    private Map<String, List<SpellGrant>> grantsOwedBySubclass(PC pc, int level) {
        List<SpellGrant> base = grantsOwed(pc, pc.getSubclass(), level);
        Map<String, List<SpellGrant>> bySubclass = new LinkedHashMap<>();
        for (String option : ClassProgression.subclassesFor(pc.getClazz(), pc.getSpecialization())) {
            List<SpellGrant> owed = grantsOwed(pc, option, level);
            if (!owed.equals(base)) bySubclass.put(option, owed);
        }
        return bySubclass;
    }

    /** Convenience overload for level-ups with no player choices. */
    public PC applyLevelUp(PC pc) {
        return applyLevelUp(pc, null, null, null);
    }

    /** Convenience overload for a subclass-only choice. */
    public PC applyLevelUp(PC pc, String chosenSubclass) {
        return applyLevelUp(pc, chosenSubclass, null, null);
    }

    /** Convenience overload for a subclass + ASI choice (no feat). */
    public PC applyLevelUp(PC pc, String chosenSubclass, Map<String, Integer> abilityIncreases) {
        return applyLevelUp(pc, chosenSubclass, abilityIncreases, null);
    }

    /** Convenience overload for subclass + ASI + feat (no new spells). */
    public PC applyLevelUp(PC pc, String chosenSubclass, Map<String, Integer> abilityIncreases,
                           String chosenFeat) {
        return applyLevelUp(pc, chosenSubclass, abilityIncreases, chosenFeat, null);
    }

    /** Convenience overload defaulting to {@link HpMode#AVERAGE} (existing behaviour). */
    public PC applyLevelUp(PC pc, String chosenSubclass, Map<String, Integer> abilityIncreases,
                           String chosenFeat, List<Map<String, Object>> newSpells) {
        return applyLevelUp(pc, chosenSubclass, abilityIncreases, chosenFeat, newSpells, HpMode.AVERAGE);
    }

    /** Convenience overload that leaves the hero-class specialization unchanged. */
    public PC applyLevelUp(PC pc, String chosenSubclass, Map<String, Integer> abilityIncreases,
                           String chosenFeat, List<Map<String, Object>> newSpells, HpMode hpMode) {
        return applyLevelUp(pc, chosenSubclass, abilityIncreases, chosenFeat, newSpells, hpMode, null);
    }

    /** Convenience overload with no Fighting Style choice. */
    public PC applyLevelUp(PC pc, String chosenSubclass, Map<String, Integer> abilityIncreases,
                           String chosenFeat, List<Map<String, Object>> newSpells, HpMode hpMode,
                           String specialization) {
        return applyLevelUp(pc, chosenSubclass, abilityIncreases, chosenFeat, newSpells, hpMode,
                specialization, null);
    }

    /**
     * Mutate the given (managed) PC in place to its next level: validate and apply the player's
     * choices (subclass; at an ASI level exactly one of an Ability Score Improvement or a feat;
     * any newly-learned spells), then bump level, add HP, recompute the proficiency bonus, and
     * rebuild the spell-slot map (for characters that have slots). The caller persists.
     *
     * <p>HP order matters: ability scores are applied <em>before</em> HP, so the new level's gain
     * uses the post-ASI CON modifier, and a CON-modifier increase additionally grants HP to every
     * prior level (the 5e retroactive rule).
     *
     * <p>A feat that lets the player pick spells ({@link FeatSpellGrants#pickRule}: Arcane Warrior,
     * Magic Connoisseur, Boon of Magic School Mastery) needs those picks in {@code newSpells},
     * each with the feat's name as its {@code grantedBy}.
     *
     * @param chosenSubclass   the player's subclass selection, or {@code null} when none applies
     * @param abilityIncreases the ASI allocation ({@code ABILITY -> points}), or {@code null}
     * @param chosenFeat       the chosen General feat (the ASI alternative), or {@code null}
     * @param newSpells        cantrips/spells learned this level (count-validated), or {@code null}
     * @param hpMode           average vs. rolled HP for this level; {@code null} means
     *                         {@link HpMode#AVERAGE}. In ROLL mode the server rolls the die — the
     *                         client supplies only the mode, never a roll result.
     * @param specialization   a new hero-class specialization (e.g. Death Knight re-Runeforging),
     *                         or {@code null} to keep the current one; only valid for a hero
     *                         subclass, and only a change where that subclass allows respec on
     *                         level-up (see {@link HeroClassRules#validateLevelUpSpecialization})
     * @param fightingStyle    the Fighting Style choice at the class's Fighting Style level (one of
     *                         {@link FeatCatalog#fightingStyleOptions}), or {@code null}; choosing
     *                         a cantrip Fighting Style ({@value FeatCatalog#BLESSED_WARRIOR},
     *                         {@value FeatCatalog#DRUIDIC_WARRIOR}, {@value FeatCatalog#VOID_WARRIOR})
     *                         requires exactly {@value FeatCatalog#CANTRIP_STYLE_CANTRIPS} extra
     *                         cantrips in {@code newSpells}
     */
    public PC applyLevelUp(PC pc, String chosenSubclass, Map<String, Integer> abilityIncreases,
                           String chosenFeat, List<Map<String, Object>> newSpells, HpMode hpMode,
                           String specialization, String fightingStyle) {
        int currentLevel = currentLevel(pc);
        assertCanLevelUp(currentLevel);
        int newLevel = currentLevel + 1;

        int oldConMod = ClassProgression.abilityModifier(nz(pc.getAbilityCon()));
        int oldDexMod = ClassProgression.abilityModifier(nz(pc.getAbilityDex()));

        // Validate + apply choices first — an ASI can change ability scores (including CON).
        // A WoW-only subclass (Oath of the Holy) is unlocked by the specialization, so the subclass
        // options use the one this request sets, if any (it is validated just below).
        String effectiveSpecialization = specialization != null ? specialization : pc.getSpecialization();
        // A WoW variant that requires a subclass (the Evoker's Draconic Sorcery) is checked against
        // the post-level-up subclass + specialization before anything is mutated.
        HeroClassRules.validateRequiredSubclass(pc.getClazz(),
                isBlank(chosenSubclass) ? pc.getSubclass() : chosenSubclass.trim(), effectiveSpecialization);
        applySubclassChoice(pc, newLevel, chosenSubclass, effectiveSpecialization);
        String style = validateFightingStyleChoice(pc, newLevel, fightingStyle);
        applySpecializationChoice(pc, specialization);
        String feat = applyMilestoneChoice(pc, newLevel, abilityIncreases, chosenFeat);
        grantClassFeatures(pc, newLevel);
        applySpellChoices(pc, currentLevel, newLevel, newSpells, style, feat);
        recordFightingStyle(pc, newLevel, style);

        int newConMod = ClassProgression.abilityModifier(nz(pc.getAbilityCon()));
        int hitDie = ClassProgression.hitDie(pc.getClazz());
        int hpGained = hpCalculator.gain(hitDie, newConMod, hpMode);
        // A rise in CON's modifier grants HP to every level you already have (this level's gain
        // above already reflects the new modifier, so only the prior levels are added here).
        int retroactiveHp = (newLevel - 1) * (newConMod - oldConMod);

        pc.setLevel((short) newLevel);
        pc.setHpMax((short) (nz(pc.getHpMax()) + hpGained + retroactiveHp));
        pc.setHpCurrent((short) (nz(pc.getHpCurrent()) + hpGained + retroactiveHp));
        pc.setProfBonus((short) proficiencyBonus(pc, newLevel));
        // A saved initiative moves with the Dexterity modifier, keeping the DM's lines and any
        // hand-set rest on top of it (the sheet works initiative out the same way).
        int dexModDelta = ClassProgression.abilityModifier(nz(pc.getAbilityDex())) - oldDexMod;
        if (pc.getInitiative() != null && dexModDelta != 0) {
            pc.setInitiative((short) (pc.getInitiative() + dexModDelta));
        }

        // Every character with slots: the class table (full casters, the warlock and the half
        // casters) plus any slots the DM granted.
        pc.setSpellSlots(SpellSlotState.rebuild(pc));
        return pc;
    }

    private int currentLevel(PC pc) {
        return pc.getLevel() == null ? 1 : pc.getLevel();
    }

    /**
     * The proficiency bonus at a level: the 2024 table plus the lines the DM added to it
     * ({@code prof} in the stat adjustments, e.g. {@code +1 | Blessing}). The sheet shows the
     * bonus as those same parts, so a level-up keeps the DM's lines in the total.
     */
    static int proficiencyBonus(PC pc, int level) {
        int lines = StatAdjustmentRules.parse(pc.getStatAdjustments()).getOrDefault(PROF_KEY, List.of())
                .stream().mapToInt(StatAdjustmentRules.Line::value).sum();
        return ClassProgression.proficiencyBonusForLevel(level) + lines;
    }

    /** The stat-adjustment key holding the DM's lines on the proficiency bonus. */
    static final String PROF_KEY = "prof";

    private void assertCanLevelUp(int currentLevel) {
        if (currentLevel >= ClassProgression.MAX_LEVEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Character is already at the maximum level (" + ClassProgression.MAX_LEVEL + ")");
        }
    }

    // ── Subclass selection (Phase 3) ─────────────────────────────────────────────

    /** A subclass choice is due when the new level is the class's grant level and none is set yet. */
    private boolean subclassDue(PC pc, int newLevel) {
        return newLevel == ClassProgression.subclassLevelFor(pc.getClazz())
                && isBlank(pc.getSubclass());
    }

    /**
     * Validate and apply the player's subclass choice for this level. Server-authoritative:
     * a choice is only accepted when it is actually due, and (once catalog content exists) it
     * must be a known subclass for the class + specialization (WoW-only subclasses such as Oath of
     * the Holy are valid only for that class's WoW variant — see
     * {@link ClassProgression#subclassesFor(String, String)}). With the catalog empty, the flow is
     * dormant — no choice is required and none is offered.
     */
    private void applySubclassChoice(PC pc, int newLevel, String chosen, String specialization) {
        boolean due = subclassDue(pc, newLevel);
        List<String> options = ClassProgression.subclassesFor(pc.getClazz(), specialization);

        if (chosen != null && !chosen.isBlank()) {
            String choice = chosen.trim();
            if (!due) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A subclass cannot be selected for " + pc.getClazz() + " at level " + newLevel);
            }
            if (!options.isEmpty() && options.stream().noneMatch(o -> o.equalsIgnoreCase(choice))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + choice + "' is not a valid subclass for " + pc.getClazz());
            }
            pc.setSubclass(choice);
            return;
        }

        // No choice supplied: only an error when a real catalog exists and a pick is due.
        if (due && !options.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A subclass must be selected at level " + newLevel);
        }
    }

    /**
     * Hero-class specialization chosen on level-up (e.g. Death Knight re-Runeforging). Whether an
     * existing one may be changed depends on the hero subclass's respec policy.
     */
    private void applySpecializationChoice(PC pc, String specialization) {
        if (specialization == null) return;
        HeroClassRules.validateLevelUpSpecialization(pc.getClazz(), pc.getSubclass(),
                pc.getSpecialization(), specialization);
        pc.setSpecialization(specialization);
    }

    // ── Fighting Style class-feature choice ──────────────────────────────────────

    /**
     * Validate the optional Fighting Style choice: only at the class's Fighting Style level, and
     * only one of {@link FeatCatalog#fightingStyleOptions} (so {@value FeatCatalog#VOID_WARRIOR}
     * is rejected for anyone but a Demon Hunter ranger). Returns the canonical option name, or
     * {@code null} when none was chosen. Optional — omitting it keeps older clients working.
     */
    private String validateFightingStyleChoice(PC pc, int newLevel, String fightingStyle) {
        if (isBlank(fightingStyle)) return null;
        String name = fightingStyle.trim();
        if (!FeatCatalog.fightingStyleDue(pc.getClazz(), newLevel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No Fighting Style choice is available for " + pc.getClazz() + " at level " + newLevel);
        }
        String match = FeatCatalog.matchFightingStyle(name, pc.getClazz(), pc.getSubclass(), newLevel);
        if (match == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + name + "' is not a selectable Fighting Style");
        }
        return match;
    }

    /** Record the chosen Fighting Style among the PC's features. */
    private void recordFightingStyle(PC pc, int newLevel, String style) {
        if (style == null) return;
        String desc = FeatCatalog.cantripFightingStyle(style).map(FeatCatalog.CantripFightingStyle::desc).orElse("");
        List<Map<String, Object>> features = jsonColumns.parse(pc.getFeatures());
        features.add(jsonColumns.featureEntry(style, "Fighting Style (Level " + newLevel + ")", desc));
        pc.setFeatures(jsonColumns.write(features));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ── ASI-or-feat milestone choice (Phase 4 + feats) ───────────────────────────

    private static final List<String> ABILITIES = List.of("STR", "DEX", "CON", "INT", "WIS", "CHA");

    /**
     * At an ASI level the player takes exactly one of an Ability Score Improvement or a General
     * feat; at any other level neither is allowed. Server-authoritative gating; the chosen branch
     * then validates and applies itself. Returns the feat taken (its exact name), or {@code null}.
     */
    private String applyMilestoneChoice(PC pc, int newLevel, Map<String, Integer> increases, String feat) {
        boolean asiLevel = ClassProgression.isAsiLevel(pc.getClazz(), newLevel);
        boolean hasAsi = increases != null && !increases.isEmpty();
        boolean hasFeat = feat != null && !feat.isBlank();

        if (!asiLevel) {
            if (hasAsi || hasFeat) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No ability score improvement or feat is available at level " + newLevel);
            }
            return null;
        }
        if (hasAsi && hasFeat) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose either an ability score improvement or a feat, not both");
        }
        if (!hasAsi && !hasFeat) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An ability score improvement or feat is required at level " + newLevel);
        }
        if (hasAsi) {
            applyAsiAllocation(pc, newLevel, increases);
            return null;
        }
        return applyFeatChoice(pc, newLevel, feat);
    }

    /**
     * Validate and apply an ASI allocation: exactly 2 points (+2 to one ability or +1 to two
     * distinct ones), no score past {@value ClassProgression#MAX_ABILITY_SCORE}.
     *
     * <p>Each raised score also gets a labelled line, {@code Level N Increase}, among the PC's
     * stat adjustments, so the sheet's ability breakdown says where the points came from. A score
     * that already carries the most lines it may hold still rises; only the line is left out
     * (see {@link StatAdjustmentRules#appendLine}).
     */
    private void applyAsiAllocation(PC pc, int newLevel, Map<String, Integer> increases) {
        // Normalize keys and total the points.
        Map<String, Integer> allocation = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : increases.entrySet()) {
            String ability = e.getKey() == null ? "" : e.getKey().trim().toUpperCase();
            Integer points = e.getValue();
            if (!ABILITIES.contains(ability)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown ability: " + e.getKey());
            }
            if (points == null || points < 1 || points > 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each ability increase must be 1 or 2 points");
            }
            allocation.merge(ability, points, Integer::sum);
            total += points;
        }
        if (total != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An ability score improvement must add exactly 2 points (+2 to one ability or +1 to two)");
        }

        // Validate caps before mutating anything, so a bad allocation leaves the PC untouched.
        allocation.forEach((ability, points) -> {
            if (abilityScore(pc, ability) + points > ClassProgression.MAX_ABILITY_SCORE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        ability + " cannot exceed " + ClassProgression.MAX_ABILITY_SCORE);
            }
        });
        allocation.forEach((ability, points) -> {
            setAbilityScore(pc, ability, (short) (abilityScore(pc, ability) + points));
            pc.setStatAdjustments(StatAdjustmentRules.appendLine(
                    pc.getStatAdjustments(), ability, points, "Level " + newLevel + " Increase"));
        });
    }

    /**
     * Validate the chosen feat against the catalog and record it among the PC's features, under
     * the catalog's spelling of its name. Every prerequisite is checked against the PC as it is
     * before this level-up: ability scores, the Spellcasting or Pact Magic feature, and the feats
     * already on the character ({@link CharacterFeats}). A feat refused for a prerequisite says
     * which one. Returns the feat's name.
     */
    private String applyFeatChoice(PC pc, int newLevel, String feat) {
        String name = feat.trim();
        FeatCatalog.Candidate candidate = candidate(pc, newLevel);
        String match = FeatCatalog.matchFeat(name, candidate);
        if (match == null) {
            String unmet = FeatCatalog.unmetPrerequisite(name, candidate);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, unmet != null
                    ? "'" + name + "' " + unmet
                    : "'" + name + "' is not a selectable feat");
        }
        List<Map<String, Object>> features = jsonColumns.parse(pc.getFeatures());
        features.add(jsonColumns.featureEntry(match, "Feat (Level " + newLevel + ")", ""));
        pc.setFeatures(jsonColumns.write(features));
        return match;
    }

    /**
     * Append the class features (if any) gained at the new level to the PC's features list. Uses the
     * PC's (already-updated) specialization, so a WoW variant's feature swaps apply (e.g. a WoW
     * Shaman gains Totems instead of Primal Knowledge).
     */
    private void grantClassFeatures(PC pc, int newLevel) {
        List<FeatureGain> gained = ClassFeatures.featuresAt(pc.getClazz(), pc.getSubclass(), pc.getSpecialization(), newLevel);
        if (gained.isEmpty()) return;
        List<Map<String, Object>> features = jsonColumns.parse(pc.getFeatures());
        String source = pc.getClazz() + " " + newLevel;
        for (FeatureGain g : gained) {
            features.add(jsonColumns.featureEntry(g.name(), source, g.desc()));
        }
        pc.setFeatures(jsonColumns.write(features));
    }

    // ── Spell selection (learning new cantrips/spells) ───────────────────────────

    /**
     * Append the player's newly-learned cantrips/spells to the PC's {@code spells} list.
     *
     * <p>Server-authoritative on <em>count</em> and <em>spell level</em>: the number of new
     * cantrips (spell level 0) may not exceed the cantrips-known delta, the number of new leveled
     * spells may not exceed the prepared/known-spells delta, and no leveled spell may be of a
     * higher level than the character has spell slots for at the NEW level (2024 5e: you can only
     * learn spells you have slots to cast — the max key of {@link ClassProgression#spellSlotsFor},
     * which also covers warlock pact slots). Duplicates (by name, case-insensitive) are rejected.
     * Individual spell <em>names</em> are NOT validated — the spell list lives in the frontend
     * (the backend must not depend on the external D&D API), so the client-supplied spell objects
     * are accepted as-is. Same trust posture as feats and subclasses.
     *
     * <p>{@code fightingStyle} is the Fighting Style chosen in the same request; when it is a
     * cantrip Fighting Style ({@link FeatCatalog#cantripFightingStyle} — Blessed Warrior, Druidic
     * Warrior, Void Warrior) the request must carry <em>exactly</em> the class's cantrip delta plus
     * the style's extra cantrips — the count is all that is checked (the server has no spell list,
     * so it can't confirm they are Cleric/Druid/Warlock cantrips).
     *
     * <p>A spell named by {@link ClassSpellGrants#grantsThrough} for the new level (a Paladin's
     * Find Steed at 5, the spells of the oath chosen in this same request) is a feature's
     * always-prepared spell, not a pick: it is left out of both counts and the spell-level cap,
     * and saved with {@code alwaysPrepared} and {@code grantedBy} set here, so it never counts
     * against the prepared-spell limit. If the character already has the spell, the existing entry
     * is marked instead. Grants from earlier levels are accepted too, so a character that passed
     * a level before its grant existed catches up. The app sends these with the picks (the
     * preview lists their names); a request without them simply adds none.
     *
     * <p>The spells of the character's feats ({@link FeatSpellGrants#grantsThrough}, the feat taken
     * in this request included) are handled the same way: outside the counts and the cap, saved
     * always prepared with the feat as {@code grantedBy}.
     *
     * <p>{@code feat} is the feat taken in this request. When it lets the player pick spells
     * ({@link FeatSpellGrants#pickRule}) the request must carry exactly those picks, each with the
     * feat's name as its {@code grantedBy}: the right number at the right spell levels, and of the
     * feat's school when it has one (read from the spell sent; whether a spell is on a class's
     * list cannot be checked here). They too sit outside the class counts and the slot cap, and
     * are saved with the feat as {@code grantedBy}, a leveled one always prepared.
     */
    private void applySpellChoices(PC pc, int currentLevel, int newLevel,
                                   List<Map<String, Object>> chosen, String fightingStyle, String feat) {
        FeatCatalog.CantripFightingStyle cantripStyle = FeatCatalog.cantripFightingStyle(fightingStyle).orElse(null);
        int bonusCantrips = cantripStyle == null ? 0 : cantripStyle.extraCantrips();
        FeatSpellGrants.PickRule pickRule = FeatSpellGrants.pickRule(feat).orElse(null);
        if ((chosen == null || chosen.isEmpty()) && bonusCantrips == 0 && pickRule == null) return;
        if (chosen == null) chosen = List.of();

        // The highest spell level the character has slots for at the new level (0 = none).
        int maxSpellLevel = ClassProgression.spellSlotsFor(pc.getClazz(), newLevel).keySet().stream()
                .max(Integer::compareTo).orElse(0);

        int cantripDelta = ClassProgression.cantripsKnownFor(pc.getClazz(), newLevel)
                - ClassProgression.cantripsKnownFor(pc.getClazz(), currentLevel);
        int spellDelta = ClassProgression.preparedSpellsFor(pc.getClazz(), newLevel)
                - ClassProgression.preparedSpellsFor(pc.getClazz(), currentLevel);

        // The subclass and the feat chosen in this request are already on the PC, so their spells
        // are recognized.
        List<SpellGrant> grants = new ArrayList<>(ClassSpellGrants.grantsThrough(pc.getClazz(),
                pc.getSubclass(), pc.getSpecialization(), newLevel));
        grants.addAll(FeatSpellGrants.grantsThrough(CharacterFeats.namesOn(pc), pc.getClazz(),
                pc.getSubclass(), newLevel));

        int newCantrips = 0;
        int newLeveled = 0;
        List<Map<String, Object>> featPicks = new ArrayList<>();
        for (Map<String, Object> spell : chosen) {
            if (isFeatPick(pickRule, spell)) {
                featPicks.add(spell);
                continue;
            }
            if (grantFor(grants, spellName(spell)) != null) continue;
            if (spellLevelOf(spell) == 0) newCantrips++;
            else newLeveled++;
        }
        if (pickRule != null) validateFeatPicks(pickRule, featPicks);
        if (bonusCantrips > 0) {
            int required = Math.max(0, cantripDelta) + bonusCantrips;
            if (newCantrips != required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        cantripStyle.name() + " requires exactly " + required
                                + " new cantrip(s) at level " + newLevel);
            }
        } else if (newCantrips > Math.max(0, cantripDelta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You can learn at most " + Math.max(0, cantripDelta) + " new cantrip(s) at level " + newLevel);
        }
        if (newLeveled > Math.max(0, spellDelta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You can learn at most " + Math.max(0, spellDelta) + " new spell(s) at level " + newLevel);
        }

        List<Map<String, Object>> spells = jsonColumns.parse(pc.getSpells());
        java.util.Set<String> known = new java.util.HashSet<>();
        for (Map<String, Object> existing : spells) {
            known.add(spellName(existing).toLowerCase());
        }
        for (Map<String, Object> spell : chosen) {
            String name = spellName(spell);
            if (name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each spell must have a name");
            }
            if (isFeatPick(pickRule, spell)) {
                // A spell picked for the feat taken this level. A leveled one the character
                // already has becomes the feat's; a cantrip it already knows would be a wasted pick.
                boolean cantrip = spellLevelOf(spell) == 0;
                Map<String, Object> entry = spells.stream()
                        .filter(existing -> spellName(existing).equalsIgnoreCase(name))
                        .findFirst().orElse(null);
                if (entry != null && cantrip) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + name + "' is already known");
                }
                if (entry == null) {
                    entry = new LinkedHashMap<>(spell);
                    spells.add(entry);
                    known.add(name.toLowerCase());
                }
                entry.put("prepared", true);
                if (!cantrip) entry.put("alwaysPrepared", true);
                entry.put("grantedBy", pickRule.feat());
                continue;
            }
            SpellGrant grant = grantFor(grants, name);
            if (grant != null) {
                // A class feature's always-prepared spell: mark the entry the character already
                // has, or save the one sent with the marker on it.
                Map<String, Object> entry = spells.stream()
                        .filter(existing -> spellName(existing).equalsIgnoreCase(name))
                        .findFirst().orElse(null);
                if (entry == null) {
                    entry = new LinkedHashMap<>(spell);
                    spells.add(entry);
                    known.add(name.toLowerCase());
                }
                entry.put("prepared", true);
                entry.put("alwaysPrepared", true);
                entry.put("grantedBy", grant.grantedBy());
                continue;
            }
            int spellLevel = spellLevelOf(spell);
            if (spellLevel > maxSpellLevel) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + name + "' is a level-" + spellLevel + " spell — the highest spell level "
                                + "castable at level " + newLevel + " is " + maxSpellLevel);
            }
            if (!known.add(name.toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + name + "' is already known");
            }
            if (spell.containsKey("alwaysPrepared")) {
                // An ordinary pick: the marker is the server's to set (from its grant table), so
                // one sent by the client is dropped and the spell counts against the limit.
                spell = new LinkedHashMap<>(spell);
                spell.remove("alwaysPrepared");
                spell.remove("grantedBy");
            }
            spells.add(spell);
        }
        pc.setSpells(jsonColumns.write(spells));
    }

    /** True when the spell is marked as a pick for the feat taken in this request. */
    private static boolean isFeatPick(FeatSpellGrants.PickRule pickRule, Map<String, Object> spell) {
        Object grantedBy = spell.get("grantedBy");
        return pickRule != null && grantedBy != null
                && pickRule.feat().equalsIgnoreCase(grantedBy.toString().trim());
    }

    /**
     * The picks sent for a feat must be exactly what the feat asks for: the right number of
     * spells at the right spell levels, no spell twice, and all of the feat's school if it has one.
     */
    private void validateFeatPicks(FeatSpellGrants.PickRule rule, List<Map<String, Object>> picks) {
        List<Integer> levels = picks.stream().map(this::spellLevelOf).toList();
        Set<String> distinct = new HashSet<>();
        picks.forEach(spell -> distinct.add(spellName(spell).trim().toLowerCase()));
        if (!rule.satisfiedBy(levels) || distinct.size() != picks.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, rule.feat() + " requires " + rule.needs());
        }
        if (rule.school() == null) return;
        for (Map<String, Object> spell : picks) {
            Object school = spell.get("school");
            if (school == null || !rule.school().equalsIgnoreCase(school.toString().trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + spellName(spell) + "' is not a spell of the " + rule.school() + " school");
            }
        }
    }

    /** The grant naming this spell (case-insensitive, trimmed), or null when it is an ordinary pick. */
    private static SpellGrant grantFor(List<SpellGrant> grants, String spellName) {
        String name = spellName == null ? "" : spellName.trim();
        return grants.stream().filter(g -> g.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private int spellLevelOf(Map<String, Object> spell) {
        Object lvl = spell.get("lvl");
        return lvl instanceof Number n ? n.intValue() : 0;
    }

    private String spellName(Map<String, Object> spell) {
        Object name = spell.get("name");
        return name == null ? "" : name.toString();
    }

    // ── Ability-score access ─────────────────────────────────────────────────────

    /** The PC's six ability scores keyed by {@link #ABILITIES} (nulls as 0), for feat prerequisites. */
    private Map<String, Integer> abilityScores(PC pc) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String ability : ABILITIES) scores.put(ability, abilityScore(pc, ability));
        return scores;
    }

    private int abilityScore(PC pc, String ability) {
        return switch (ability) {
            case "STR" -> nz(pc.getAbilityStr());
            case "DEX" -> nz(pc.getAbilityDex());
            case "CON" -> nz(pc.getAbilityCon());
            case "INT" -> nz(pc.getAbilityInt());
            case "WIS" -> nz(pc.getAbilityWis());
            case "CHA" -> nz(pc.getAbilityCha());
            default -> 0;
        };
    }

    private void setAbilityScore(PC pc, String ability, short value) {
        switch (ability) {
            case "STR" -> pc.setAbilityStr(value);
            case "DEX" -> pc.setAbilityDex(value);
            case "CON" -> pc.setAbilityCon(value);
            case "INT" -> pc.setAbilityInt(value);
            case "WIS" -> pc.setAbilityWis(value);
            case "CHA" -> pc.setAbilityCha(value);
            default -> { /* unreachable — validated above */ }
        }
    }

    /** Null-safe read of a nullable Short stat as a primitive (older PCs may have null combat stats). */
    private static int nz(Short value) {
        return value == null ? 0 : value;
    }
}
