package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FeatureGain;
import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassFeatures;
import com.moo.charactermanagerservice.progression.ClassProgression;
import com.moo.charactermanagerservice.progression.FeatCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Server-authoritative level-up rules engine (single-class, D&D 5e 2024).
 *
 * <p><strong>Single responsibility:</strong> orchestrate the deterministic gains of advancing one
 * level — validate the player's choices, then apply them in the right order. It does not load,
 * persist, or authorize ({@link PCService} owns that and delegates here), and it delegates the
 * mechanical sub-tasks to collaborators: {@link HpCalculator} (HP-per-level + the RNG seam),
 * {@link SpellSlotCalculator} (the {@code spellSlots} JSON), and {@link PcJsonColumns} (the
 * {@code features}/{@code spells} JSON columns). Keeping the rules out of the controller and out of
 * PCService means the math is unit tested in isolation (no repo, no Spring context, no security).
 *
 * <p><strong>HP policy:</strong> per request, either the fixed average or a roll ({@link HpMode}) —
 * see {@link HpCalculator}. The mode defaults to {@link HpMode#AVERAGE} so behaviour is unchanged
 * when none is sent. Because a roll isn't known until commit, the {@link #preview(PC) preview}
 * always reports the average (the floor/best-case estimate).
 *
 * <p><strong>Spell slots (Phase 2):</strong> for caster classes, the {@code spellSlots} column is
 * rebuilt from {@link ClassProgression#spellSlotsFor} via {@link SpellSlotCalculator} — {@code max}
 * is set from the table for the new level while the player's {@code used} count is preserved
 * (clamped to the new max). Non-casters are left untouched.
 *
 * <p><strong>Multiclass seam:</strong> today total level == single class level, so all tables key
 * off {@code pc.clazz} / {@code pc.level}. When multiclassing lands, hit-die/slot lookups move to
 * {@code (class, classLevel)} (slots via the multiclass caster-level rule) while proficiency bonus
 * stays on total level — only the inputs change, not this contract.
 */
@Service
public class LevelUpService {

    private final HpCalculator hpCalculator;
    private final SpellSlotCalculator spellSlotCalculator;
    private final PcJsonColumns jsonColumns;

    @Autowired
    public LevelUpService(ObjectMapper objectMapper) {
        this(objectMapper, new Random());
    }

    /** Test seam: inject a seeded/fixed RNG so rolled-HP outcomes are deterministic. */
    LevelUpService(ObjectMapper objectMapper, RandomGenerator rng) {
        this.hpCalculator = new HpCalculator(rng);
        this.spellSlotCalculator = new SpellSlotCalculator(objectMapper);
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

        return new LevelUpPreview(
                currentLevel,
                newLevel,
                hitDie,
                conMod,
                hpGained,
                nz(pc.getHpMax()) + hpGained,
                ClassProgression.proficiencyBonusForLevel(currentLevel),
                ClassProgression.proficiencyBonusForLevel(newLevel),
                spellSlotCalculator.currentMax(pc.getSpellSlots()),
                ClassProgression.spellSlotsFor(pc.getClazz(), newLevel),
                subclassDue(pc, newLevel),
                ClassProgression.subclassesFor(pc.getClazz()),
                asiDue,
                asiDue ? FeatCatalog.generalFeats() : List.of(),
                ClassFeatures.featuresAt(pc.getClazz(), newLevel),
                ClassProgression.cantripsKnownFor(pc.getClazz(), currentLevel),
                ClassProgression.cantripsKnownFor(pc.getClazz(), newLevel),
                ClassProgression.preparedSpellsFor(pc.getClazz(), currentLevel),
                ClassProgression.preparedSpellsFor(pc.getClazz(), newLevel)
        );
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

    /**
     * Mutate the given (managed) PC in place to its next level: validate and apply the player's
     * choices (subclass; at an ASI level exactly one of an Ability Score Improvement or a feat;
     * any newly-learned spells), then bump level, add HP, recompute the proficiency bonus, and
     * rebuild the spell-slot map (for casters). The caller persists.
     *
     * <p>HP order matters: ability scores are applied <em>before</em> HP, so the new level's gain
     * uses the post-ASI CON modifier, and a CON-modifier increase additionally grants HP to every
     * prior level (the 5e retroactive rule).
     *
     * @param chosenSubclass   the player's subclass selection, or {@code null} when none applies
     * @param abilityIncreases the ASI allocation ({@code ABILITY -> points}), or {@code null}
     * @param chosenFeat       the chosen General feat (the ASI alternative), or {@code null}
     * @param newSpells        cantrips/spells learned this level (count-validated), or {@code null}
     * @param hpMode           average vs. rolled HP for this level; {@code null} means
     *                         {@link HpMode#AVERAGE}. In ROLL mode the server rolls the die — the
     *                         client supplies only the mode, never a roll result.
     */
    public PC applyLevelUp(PC pc, String chosenSubclass, Map<String, Integer> abilityIncreases,
                           String chosenFeat, List<Map<String, Object>> newSpells, HpMode hpMode) {
        int currentLevel = currentLevel(pc);
        assertCanLevelUp(currentLevel);
        int newLevel = currentLevel + 1;

        int oldConMod = ClassProgression.abilityModifier(nz(pc.getAbilityCon()));

        // Validate + apply choices first — an ASI can change ability scores (including CON).
        applySubclassChoice(pc, newLevel, chosenSubclass);
        applyMilestoneChoice(pc, newLevel, abilityIncreases, chosenFeat);
        grantClassFeatures(pc, newLevel);
        applySpellChoices(pc, currentLevel, newLevel, newSpells);

        int newConMod = ClassProgression.abilityModifier(nz(pc.getAbilityCon()));
        int hitDie = ClassProgression.hitDie(pc.getClazz());
        int hpGained = hpCalculator.gain(hitDie, newConMod, hpMode);
        // A rise in CON's modifier grants HP to every level you already have (this level's gain
        // above already reflects the new modifier, so only the prior levels are added here).
        int retroactiveHp = (newLevel - 1) * (newConMod - oldConMod);

        pc.setLevel((short) newLevel);
        pc.setHpMax((short) (nz(pc.getHpMax()) + hpGained + retroactiveHp));
        pc.setHpCurrent((short) (nz(pc.getHpCurrent()) + hpGained + retroactiveHp));
        pc.setProfBonus((short) ClassProgression.proficiencyBonusForLevel(newLevel));

        if (ClassProgression.isCaster(pc.getClazz())) {
            pc.setSpellSlots(spellSlotCalculator.rebuild(
                    pc.getSpellSlots(), ClassProgression.spellSlotsFor(pc.getClazz(), newLevel)));
        }
        return pc;
    }

    private int currentLevel(PC pc) {
        return pc.getLevel() == null ? 1 : pc.getLevel();
    }

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
     * must be a known subclass for the class. With the catalog empty, the flow is dormant — no
     * choice is required and none is offered — so today this is effectively a no-op.
     */
    private void applySubclassChoice(PC pc, int newLevel, String chosen) {
        boolean due = subclassDue(pc, newLevel);
        List<String> options = ClassProgression.subclassesFor(pc.getClazz());

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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ── ASI-or-feat milestone choice (Phase 4 + feats) ───────────────────────────

    private static final List<String> ABILITIES = List.of("STR", "DEX", "CON", "INT", "WIS", "CHA");

    /**
     * At an ASI level the player takes exactly one of an Ability Score Improvement or a General
     * feat; at any other level neither is allowed. Server-authoritative gating; the chosen branch
     * then validates and applies itself.
     */
    private void applyMilestoneChoice(PC pc, int newLevel, Map<String, Integer> increases, String feat) {
        boolean asiLevel = ClassProgression.isAsiLevel(pc.getClazz(), newLevel);
        boolean hasAsi = increases != null && !increases.isEmpty();
        boolean hasFeat = feat != null && !feat.isBlank();

        if (!asiLevel) {
            if (hasAsi || hasFeat) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No ability score improvement or feat is available at level " + newLevel);
            }
            return;
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
            applyAsiAllocation(pc, increases);
        } else {
            applyFeatChoice(pc, newLevel, feat);
        }
    }

    /**
     * Validate and apply an ASI allocation: exactly 2 points (+2 to one ability or +1 to two
     * distinct ones), no score past {@value ClassProgression#MAX_ABILITY_SCORE}.
     */
    private void applyAsiAllocation(PC pc, Map<String, Integer> increases) {
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
        allocation.forEach((ability, points) ->
                setAbilityScore(pc, ability, (short) (abilityScore(pc, ability) + points)));
    }

    /** Validate the chosen feat against the catalog and record it among the PC's features. */
    private void applyFeatChoice(PC pc, int newLevel, String feat) {
        String name = feat.trim();
        if (!FeatCatalog.isValidFeat(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + name + "' is not a selectable feat");
        }
        List<Map<String, Object>> features = jsonColumns.parse(pc.getFeatures());
        features.add(jsonColumns.featureEntry(name, "Feat (Level " + newLevel + ")", ""));
        pc.setFeatures(jsonColumns.write(features));
    }

    /** Append the class features (if any) gained at the new level to the PC's features list. */
    private void grantClassFeatures(PC pc, int newLevel) {
        List<FeatureGain> gained = ClassFeatures.featuresAt(pc.getClazz(), newLevel);
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
     */
    private void applySpellChoices(PC pc, int currentLevel, int newLevel,
                                   List<Map<String, Object>> chosen) {
        if (chosen == null || chosen.isEmpty()) return;

        // The highest spell level the character has slots for at the new level (0 = none).
        int maxSpellLevel = ClassProgression.spellSlotsFor(pc.getClazz(), newLevel).keySet().stream()
                .max(Integer::compareTo).orElse(0);

        int cantripDelta = ClassProgression.cantripsKnownFor(pc.getClazz(), newLevel)
                - ClassProgression.cantripsKnownFor(pc.getClazz(), currentLevel);
        int spellDelta = ClassProgression.preparedSpellsFor(pc.getClazz(), newLevel)
                - ClassProgression.preparedSpellsFor(pc.getClazz(), currentLevel);

        int newCantrips = 0;
        int newLeveled = 0;
        for (Map<String, Object> spell : chosen) {
            if (spellLevelOf(spell) == 0) newCantrips++;
            else newLeveled++;
        }
        if (newCantrips > Math.max(0, cantripDelta)) {
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
            int spellLevel = spellLevelOf(spell);
            if (spellLevel > maxSpellLevel) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + name + "' is a level-" + spellLevel + " spell — the highest spell level "
                                + "castable at level " + newLevel + " is " + maxSpellLevel);
            }
            if (!known.add(name.toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + name + "' is already known");
            }
            spells.add(spell);
        }
        pc.setSpells(jsonColumns.write(spells));
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
