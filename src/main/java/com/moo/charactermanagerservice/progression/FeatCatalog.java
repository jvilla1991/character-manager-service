package com.moo.charactermanagerservice.progression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Selectable feats (D&D 5e 2024) — the feat option at an Ability Score Improvement level.
 *
 * <p>This is the server-authoritative source of truth for <em>which</em> feats may be chosen on
 * level-up; it holds names only. Descriptions are presentation and live in the frontend.
 *
 * <p>Three 2024 PHB categories are selectable, with prerequisite gating:
 * <ul>
 *   <li><b>General feats</b> — always offered at an ASI level, together with the World of
 *       Warcraft module's General feats (level 4+; the monk ones also need Dexterity 13+ and
 *       Wisdom 13+ — see {@link #featOptions(String, int, Map)}).</li>
 *   <li><b>Fighting Style feats</b> — offered only to classes with the Fighting Style class
 *       feature (Fighter, Paladin, Ranger).</li>
 *   <li><b>Epic Boon feats</b> — offered only at level 19+.</li>
 * </ul>
 *
 * <p>The Arcana Unleashed book adds fifteen General feats, one Fighting Style feat
 * ({@value #ARCANE_WARRIOR}) and three Epic Boons, with three more kinds of prerequisite: the
 * Spellcasting or Pact Magic feature ({@link ClassProgression#hasSpellcastingOrPactMagic}), the
 * {@value #FAMILIAR_FRIEND} feat, and a {@value #MAGIC_INITIATE} feat. Those need the feats the
 * character already has, so they are checked through a {@link Candidate}. An Arcana Unleashed
 * feat is taken once; {@value #SCHOOL_MASTERY} is taken once per school of magic and is listed
 * (and stored) per school: {@code "Boon of Magic School Mastery (Evocation)"}.
 *
 * <p>Origin feats (Alert, Tough, Lucky, Magic Initiate, …) are deliberately excluded: those are
 * granted at level 1 by a background, not chosen at an ASI level.
 *
 * <p>Also home to the <b>Fighting Style class-feature choice</b> made on level-up at the class's
 * Fighting Style level (Paladin and Ranger at 2; a Fighter's is at level 1, i.e. creation): the
 * Fighting Style feats, plus the cantrip Fighting Styles the character qualifies for
 * ({@value #BLESSED_WARRIOR} for paladins, {@value #DRUIDIC_WARRIOR} for rangers,
 * {@value #VOID_WARRIOR} for a Demon Hunter ranger only). Those three are never selectable as
 * feats at an ASI level; {@value #ARCANE_WARRIOR} teaches cantrips too but is a Fighting Style
 * feat, so it is offered in both places.
 */
public final class FeatCatalog {

    private FeatCatalog() {}

    /** Classes whose Fighting Style class feature unlocks the Fighting Style feats. */
    private static final Set<String> FIGHTING_STYLE_CLASSES = Set.of("fighter", "paladin", "ranger");

    /** All 42 General feats from the 2024 PHB (level 4+, the ASI alternative). */
    private static final Set<String> GENERAL_FEATS = Set.of(
            "Actor",
            "Athlete",
            "Charger",
            "Chef",
            "Crossbow Expert",
            "Crusher",
            "Defensive Duelist",
            "Dual Wielder",
            "Durable",
            "Elemental Adept",
            "Fey Touched",
            "Grappler",
            "Great Weapon Master",
            "Heavily Armored",
            "Heavy Armor Master",
            "Inspiring Leader",
            "Keen Mind",
            "Lightly Armored",
            "Mage Slayer",
            "Martial Weapon Training",
            "Medium Armor Master",
            "Moderately Armored",
            "Mounted Combatant",
            "Observant",
            "Piercer",
            "Poisoner",
            "Polearm Master",
            "Resilient",
            "Ritual Caster",
            "Sentinel",
            "Shadow Touched",
            "Sharpshooter",
            "Shield Master",
            "Skill Expert",
            "Skulker",
            "Slasher",
            "Speedy",
            "Spell Sniper",
            "Telekinetic",
            "Telepathic",
            "War Caster",
            "Weapon Master"
    );

    /** The level prerequisite every General feat carries (PHB 2024 and World of Warcraft alike). */
    public static final int GENERAL_FEAT_MIN_LEVEL = 4;

    /**
     * World of Warcraft tabletop module General feats (prerequisite: level 4+). Written primarily
     * for mages, but — like any General feat — offered to every class at an ASI level. Each gives
     * +1 Intelligence (max 20), which, as with the PHB half-feats, the player records via the
     * normal sheet flow; the server records the feat name only.
     */
    private static final Set<String> WOW_GENERAL_FEATS = Set.of(
            "Clearcasting",
            "Fingers of Frost",
            "Hot Streak"
    );

    /** Minimum Dexterity and Wisdom the WoW monk General feats require. */
    public static final int MONK_FEAT_MIN_SCORE = 13;

    /**
     * World of Warcraft tabletop module General feats written for monks (prerequisite: level 4+,
     * Dexterity 13+ and Wisdom 13+). Offered to any class meeting the prerequisites. Each gives +1
     * Dexterity or Wisdom (max 20), which the player records via the normal sheet flow; the server
     * records the feat name only.
     */
    private static final Set<String> WOW_MONK_FEATS = Set.of(
            "Elusive Brawler",
            "Enveloping Mist",
            "Fists of Fury",
            "Stagger and Sway"
    );

    /**
     * Ability-score prerequisites, as data: feat name → (ability key → minimum score). Ability keys
     * are the upper-case abbreviations LevelUpService uses (STR, DEX, CON, INT, WIS, CHA). Every
     * listed ability must meet its minimum. Insertion-ordered so error messages read DEX then WIS.
     */
    private static final Map<String, Map<String, Integer>> ABILITY_PREREQUISITES = buildAbilityPrerequisites();

    private static Map<String, Map<String, Integer>> buildAbilityPrerequisites() {
        Map<String, Integer> dexWis = new LinkedHashMap<>();
        dexWis.put("DEX", MONK_FEAT_MIN_SCORE);
        dexWis.put("WIS", MONK_FEAT_MIN_SCORE);
        Map<String, Map<String, Integer>> m = new HashMap<>();
        for (String feat : WOW_MONK_FEATS) {
            m.put(feat, Collections.unmodifiableMap(dexWis));
        }
        return Map.copyOf(m);
    }

    private static final Map<String, String> ABILITY_NAMES = Map.of(
            "STR", "Strength", "DEX", "Dexterity", "CON", "Constitution",
            "INT", "Intelligence", "WIS", "Wisdom", "CHA", "Charisma");

    // ── Arcana Unleashed ─────────────────────────────────────────────────────────

    /** The Origin feat the four familiar General feats build on. */
    public static final String FAMILIAR_FRIEND = "Familiar Friend";

    /** How every Magic Initiate feat name starts ({@code "Magic Initiate (Cleric)"}). */
    public static final String MAGIC_INITIATE = "Magic Initiate";

    /** The Fighting Style feat that teaches two Wizard cantrips. */
    public static final String ARCANE_WARRIOR = "Arcane Warrior";

    /** The General feat that adds two spells from the Magic Initiate feat's class list. */
    public static final String MAGIC_CONNOISSEUR = "Magic Connoisseur";

    /** The repeatable Epic Boon; offered and stored with its school, {@code "… (Evocation)"}. */
    public static final String SCHOOL_MASTERY = "Boon of Magic School Mastery";

    /** The eight schools of magic, in the book's order. */
    public static final List<String> SCHOOLS_OF_MAGIC = List.of("Abjuration", "Conjuration", "Divination",
            "Enchantment", "Evocation", "Illusion", "Necromancy", "Transmutation");

    /** The name {@value #SCHOOL_MASTERY} is offered and stored under for one school. */
    public static String schoolMasteryFeat(String school) {
        return SCHOOL_MASTERY + " (" + school + ")";
    }

    /** The eight School Adept feats: {@code "Abjuration Adept"} … {@code "Transmutation Adept"}. */
    private static final List<String> SCHOOL_ADEPT_FEATS =
            SCHOOLS_OF_MAGIC.stream().map(school -> school + " Adept").toList();

    /** The four General feats that improve the familiar of a {@value #FAMILIAR_FRIEND}. */
    private static final List<String> FAMILIAR_FEATS = List.of(
            "Elemental Familiar", "Otherworldly Familiar", "Soothing Familiar", "Warlike Familiar");

    /** Arcana Unleashed General feats (prerequisite: level 4+, and for most one more thing). */
    private static final Set<String> ARCANA_GENERAL_FEATS = buildArcanaGeneralFeats();

    private static Set<String> buildArcanaGeneralFeats() {
        List<String> feats = new ArrayList<>(SCHOOL_ADEPT_FEATS);
        feats.addAll(FAMILIAR_FEATS);
        feats.addAll(List.of(MAGIC_CONNOISSEUR, "Spell Resistant", "Spell Subterfuge"));
        return Set.copyOf(feats);
    }

    private static final String BOON_OF_ERUPTING_SPELLPOWER = "Boon of Erupting Spellpower";

    /** Arcana Unleashed Epic Boons (level 19+), {@value #SCHOOL_MASTERY} once per school. */
    private static final Set<String> ARCANA_EPIC_BOONS = buildArcanaEpicBoons();

    private static Set<String> buildArcanaEpicBoons() {
        List<String> feats = new ArrayList<>(List.of(BOON_OF_ERUPTING_SPELLPOWER, "Boon of the Iron Mind"));
        SCHOOLS_OF_MAGIC.forEach(school -> feats.add(schoolMasteryFeat(school)));
        return Set.copyOf(feats);
    }

    /** A prerequisite that is not an ability score: a class feature, or a feat already taken. */
    private enum Requirement {
        SPELLCASTING("requires the Spellcasting or Pact Magic feature"),
        FAMILIAR_FRIEND_FEAT("requires the " + FAMILIAR_FRIEND + " feat"),
        MAGIC_INITIATE_FEAT("requires a " + MAGIC_INITIATE + " feat");

        /** Reads after the quoted feat name: {@code 'Warlike Familiar' requires the Familiar Friend feat}. */
        private final String unmet;

        Requirement(String unmet) {
            this.unmet = unmet;
        }

        boolean metBy(Candidate c) {
            return switch (this) {
                case SPELLCASTING ->
                        ClassProgression.hasSpellcastingOrPactMagic(c.clazz(), c.subclass(), c.newLevel());
                case FAMILIAR_FRIEND_FEAT -> c.has(FAMILIAR_FRIEND);
                case MAGIC_INITIATE_FEAT -> c.hasMagicInitiate();
            };
        }
    }

    /** Feat name → what it needs besides its level, class and ability scores. */
    private static final Map<String, Requirement> REQUIREMENTS = buildRequirements();

    private static Map<String, Requirement> buildRequirements() {
        Map<String, Requirement> m = new HashMap<>();
        SCHOOL_ADEPT_FEATS.forEach(feat -> m.put(feat, Requirement.SPELLCASTING));
        m.put("Spell Subterfuge", Requirement.SPELLCASTING);
        m.put(BOON_OF_ERUPTING_SPELLPOWER, Requirement.SPELLCASTING);
        SCHOOLS_OF_MAGIC.forEach(school -> m.put(schoolMasteryFeat(school), Requirement.SPELLCASTING));
        FAMILIAR_FEATS.forEach(feat -> m.put(feat, Requirement.FAMILIAR_FRIEND_FEAT));
        m.put(MAGIC_CONNOISSEUR, Requirement.MAGIC_INITIATE_FEAT);
        return Map.copyOf(m);
    }

    /** Feats a character can hold once: every selectable Arcana Unleashed feat. */
    private static final Set<String> ONCE_ONLY = buildOnceOnly();

    private static Set<String> buildOnceOnly() {
        List<String> feats = new ArrayList<>(ARCANA_GENERAL_FEATS);
        feats.addAll(ARCANA_EPIC_BOONS);
        feats.add(ARCANE_WARRIOR);
        return Set.copyOf(feats);
    }

    /**
     * The character a feat is offered to: what the level, class and ability gates need, plus the
     * feats it already has (its Origin feat and every feat or Fighting Style recorded since, by
     * name; case and surrounding spaces do not matter).
     *
     * @param newLevel      the level being reached
     * @param abilityScores current scores keyed STR/DEX/CON/INT/WIS/CHA; a missing one counts as 0
     * @param feats         names of the feats already on the character; other names are ignored
     */
    public record Candidate(String clazz, String subclass, int newLevel, Map<String, Integer> abilityScores,
                            Collection<String> feats) {

        public Candidate {
            abilityScores = abilityScores == null ? Map.of() : abilityScores;
            feats = feats == null ? List.of() : feats;
        }

        /** True when the character already has this feat. */
        boolean has(String feat) {
            return feats.stream().anyMatch(f -> f != null && f.trim().equalsIgnoreCase(feat));
        }

        /** True when the character has any Magic Initiate feat, whichever class list it draws on. */
        boolean hasMagicInitiate() {
            String prefix = MAGIC_INITIATE.toLowerCase();
            return feats.stream().anyMatch(f -> f != null && f.trim().toLowerCase().startsWith(prefix));
        }
    }

    /** The 10 Fighting Style feats from the 2024 PHB (prerequisite: Fighting Style feature). */
    private static final Set<String> FIGHTING_STYLE_FEATS = Set.of(
            "Archery",
            "Blind Fighting",
            "Defense",
            "Dueling",
            "Great Weapon Fighting",
            "Interception",
            "Protection",
            "Thrown Weapon Fighting",
            "Two-Weapon Fighting",
            "Unarmed Fighting"
    );

    /** The 12 Epic Boon feats from the 2024 PHB (prerequisite: level 19+). */
    private static final Set<String> EPIC_BOON_FEATS = Set.of(
            "Boon of Combat Prowess",
            "Boon of Dimensional Travel",
            "Boon of Energy Resistance",
            "Boon of Fate",
            "Boon of Fortitude",
            "Boon of Irresistible Offense",
            "Boon of Recovery",
            "Boon of Skill",
            "Boon of Speed",
            "Boon of Spell Recall",
            "Boon of Truesight",
            "Boon of the Night Spirit"
    );

    // ── Fighting Style class-feature choice ──────────────────────────────────────

    /**
     * Demon Hunter-only Fighting Style option, taken instead of a Fighting Style feat: the
     * character learns {@value #VOID_WARRIOR_CANTRIPS} Warlock cantrips that count as Ranger spells
     * (Wisdom is their spellcasting ability), and may swap one for another Warlock cantrip each
     * time they gain a Ranger level.
     */
    public static final String VOID_WARRIOR = "Void Warrior";

    /** Cantrips a {@value #VOID_WARRIOR} learns — required in the same level-up request. */
    public static final int VOID_WARRIOR_CANTRIPS = 2;

    /** Short summary recorded on the PC's features list when {@value #VOID_WARRIOR} is chosen. */
    public static final String VOID_WARRIOR_DESC = "You learn two Warlock cantrips of your choice; "
            + "they count as Ranger spells for you and Wisdom is your spellcasting ability for them. "
            + "Whenever you gain a Ranger level, you can swap one of them for another Warlock cantrip.";

    /** PHB 2024 Paladin alternative to a Fighting Style feat: two Cleric cantrips. */
    public static final String BLESSED_WARRIOR = "Blessed Warrior";

    /** PHB 2024 Ranger alternative to a Fighting Style feat: two Druid cantrips. */
    public static final String DRUIDIC_WARRIOR = "Druidic Warrior";

    /** Extra cantrips every cantrip Fighting Style grants — required in the same level-up request. */
    public static final int CANTRIP_STYLE_CANTRIPS = 2;

    /**
     * A Fighting Style option taken instead of a Fighting Style feat that grants cantrips.
     *
     * @param name          exact option name
     * @param desc          short summary recorded on the PC's features list
     * @param eligible      (clazz, subclass) → whether the character may take it
     * @param extraCantrips cantrips it grants; the level-up request must carry exactly the class's
     *                      cantrip allowance plus this many (count-only — the server has no spell list)
     */
    public record CantripFightingStyle(String name, String desc, BiPredicate<String, String> eligible,
                                       int extraCantrips) {}

    private static boolean isClass(String clazz, String expected) {
        return clazz != null && expected.equals(clazz.trim().toLowerCase());
    }

    private static final List<CantripFightingStyle> CANTRIP_FIGHTING_STYLES = List.of(
            new CantripFightingStyle(BLESSED_WARRIOR,
                    "You learn two Cleric cantrips of your choice; they count as Paladin spells for you and "
                            + "Charisma is your spellcasting ability for them. Whenever you gain a Paladin level, "
                            + "you can swap one of them for another Cleric cantrip.",
                    (clazz, subclass) -> isClass(clazz, "paladin"),
                    CANTRIP_STYLE_CANTRIPS),
            new CantripFightingStyle(DRUIDIC_WARRIOR,
                    "You learn two Druid cantrips of your choice; they count as Ranger spells for you and "
                            + "Wisdom is your spellcasting ability for them. Whenever you gain a Ranger level, "
                            + "you can swap one of them for another Druid cantrip.",
                    (clazz, subclass) -> isClass(clazz, "ranger"),
                    CANTRIP_STYLE_CANTRIPS),
            new CantripFightingStyle(VOID_WARRIOR, VOID_WARRIOR_DESC,
                    (clazz, subclass) -> ClassProgression.heroSubclassAllowedFor(subclass, clazz)
                            && ClassProgression.isDemonHunter(subclass),
                    VOID_WARRIOR_CANTRIPS),
            // Arcana Unleashed. Unlike the three above it is a Fighting Style feat, so it is also
            // offered at an ASI level (see levelAndClassOptions).
            new CantripFightingStyle(ARCANE_WARRIOR,
                    "You learn two Wizard cantrips of your choice. Intelligence, Wisdom or Charisma is your "
                            + "spellcasting ability for them (chosen when you take this). Whenever you gain a "
                            + "level, you can swap one of them for another Wizard cantrip.",
                    (clazz, subclass) -> hasFightingStyleFeature(clazz),
                    CANTRIP_STYLE_CANTRIPS)
    );

    /** The cantrip Fighting Style with exactly this (canonical) name, if it is one. */
    public static Optional<CantripFightingStyle> cantripFightingStyle(String name) {
        if (name == null) return Optional.empty();
        return CANTRIP_FIGHTING_STYLES.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    /** Class -> the level at which its Fighting Style choice is made on level-up. */
    private static final Map<String, Integer> FIGHTING_STYLE_CHOICE_LEVEL = Map.of(
            "paladin", 2,
            "ranger", 2
    );

    /** Whether the class makes its Fighting Style choice when reaching the given (new) level. */
    public static boolean fightingStyleDue(String clazz, int newLevel) {
        if (clazz == null) return false;
        Integer level = FIGHTING_STYLE_CHOICE_LEVEL.get(clazz.trim().toLowerCase());
        return level != null && level == newLevel;
    }

    /**
     * The Fighting Style options for a character reaching the given (new) level, sorted: empty
     * unless the choice is due; otherwise the Fighting Style feats, plus each cantrip Fighting
     * Style the character is eligible for ({@value #BLESSED_WARRIOR} for paladins,
     * {@value #DRUIDIC_WARRIOR} for rangers, {@value #VOID_WARRIOR} for a Demon Hunter ranger,
     * {@value #ARCANE_WARRIOR} for everyone with the choice).
     */
    public static List<String> fightingStyleOptions(String clazz, String subclass, int newLevel) {
        if (!fightingStyleDue(clazz, newLevel)) return List.of();
        List<String> options = new ArrayList<>(FIGHTING_STYLE_FEATS);
        for (CantripFightingStyle style : CANTRIP_FIGHTING_STYLES) {
            if (style.eligible().test(clazz, subclass)) options.add(style.name());
        }
        return options.stream().sorted().toList();
    }

    /**
     * The canonical option name matching {@code name} (case-insensitive, trimmed) among
     * {@link #fightingStyleOptions}, or {@code null} when it is not selectable.
     */
    public static String matchFightingStyle(String name, String clazz, String subclass, int newLevel) {
        if (name == null) return null;
        String trimmed = name.trim();
        return fightingStyleOptions(clazz, subclass, newLevel).stream()
                .filter(o -> o.equalsIgnoreCase(trimmed))
                .findFirst().orElse(null);
    }

    /**
     * Every feat selectable by the given class at the given (new) level with no ability scores
     * known — so any feat with an ability-score prerequisite is left out. See
     * {@link #featOptions(String, int, Map)}.
     */
    public static List<String> featOptions(String clazz, int newLevel) {
        return featOptions(clazz, newLevel, Map.of());
    }

    /**
     * Every feat selectable by the given class at the given (new) level, sorted: General feats
     * always, the World of Warcraft General feats at level {@value #GENERAL_FEAT_MIN_LEVEL}+ (the
     * monk ones only when {@code abilityScores} meets their Dexterity/Wisdom prerequisite),
     * Fighting Style feats when the class has the Fighting Style feature, Epic Boons at level 19+.
     *
     * @param abilityScores the character's current scores keyed STR/DEX/CON/INT/WIS/CHA; a missing
     *                      ability counts as 0 (fails any prerequisite on it)
     */
    public static List<String> featOptions(String clazz, int newLevel, Map<String, Integer> abilityScores) {
        return featOptions(new Candidate(clazz, null, newLevel, abilityScores, List.of()));
    }

    /**
     * Every feat the character may take at the level it is reaching, sorted: the feats its level
     * and class open ({@link #featOptions(String, int, Map)}), less every one whose prerequisite
     * it does not meet — ability scores, the Spellcasting or Pact Magic feature, the
     * {@value #FAMILIAR_FRIEND} feat, a {@value #MAGIC_INITIATE} feat — and less the Arcana
     * Unleashed feats it already has.
     */
    public static List<String> featOptions(Candidate candidate) {
        return levelAndClassOptions(candidate.clazz(), candidate.newLevel()).stream()
                .filter(f -> unmet(f, candidate) == null)
                .toList();
    }

    /**
     * The feat's exact name when the character may take it (case-insensitive, trimmed), or
     * {@code null} when it is not one of {@link #featOptions(Candidate)}.
     */
    public static String matchFeat(String name, Candidate candidate) {
        return matchFeat(name, featOptions(candidate));
    }

    /**
     * Why a feat the character's level and class open is still refused — for example
     * {@code "requires Dexterity 13 and Wisdom 13"}, {@code "requires the Spellcasting or Pact
     * Magic feature"}, {@code "requires the Familiar Friend feat"}, {@code "requires a Magic
     * Initiate feat"} or {@code "can be taken only once"} — or {@code null} when the feat is
     * unknown, not offered at this level or class, or may be taken.
     */
    public static String unmetPrerequisite(String name, Candidate candidate) {
        String feat = matchFeat(name, levelAndClassOptions(candidate.clazz(), candidate.newLevel()));
        return feat == null ? null : unmet(feat, candidate);
    }

    /** What stops the character taking this (exactly named) feat, or null when nothing does. */
    private static String unmet(String feat, Candidate c) {
        if (!meetsAbilityPrerequisites(feat, c.abilityScores())) return unmetAbilities(feat);
        Requirement requirement = REQUIREMENTS.get(feat);
        if (requirement != null && !requirement.metBy(c)) return requirement.unmet;
        if (ONCE_ONLY.contains(feat) && c.has(feat)) {
            return feat.startsWith(SCHOOL_MASTERY)
                    ? "can be taken only once for each school of magic"
                    : "can be taken only once";
        }
        return null;
    }

    /** The feats offered by level and class gating alone (ignoring every other prerequisite), sorted. */
    private static List<String> levelAndClassOptions(String clazz, int newLevel) {
        List<String> options = new ArrayList<>(GENERAL_FEATS);
        if (newLevel >= GENERAL_FEAT_MIN_LEVEL) {
            options.addAll(WOW_GENERAL_FEATS);
            options.addAll(WOW_MONK_FEATS);
            options.addAll(ARCANA_GENERAL_FEATS);
        }
        if (hasFightingStyleFeature(clazz)) {
            options.addAll(FIGHTING_STYLE_FEATS);
            options.add(ARCANE_WARRIOR);
        }
        if (newLevel >= 19) {
            options.addAll(EPIC_BOON_FEATS);
            options.addAll(ARCANA_EPIC_BOONS);
        }
        return options.stream().sorted().toList();
    }

    /**
     * Whether the given name is selectable by the given class at the given (new) level
     * (case-insensitive) with no ability scores known; see {@link #isValidFeat(String, String, int, Map)}.
     */
    public static boolean isValidFeat(String name, String clazz, int newLevel) {
        return isValidFeat(name, clazz, newLevel, Map.of());
    }

    /**
     * Whether the given name is selectable by the given class at the given (new) level with the
     * given ability scores (case-insensitive), applying the same gating as
     * {@link #featOptions(String, int, Map)}.
     */
    public static boolean isValidFeat(String name, String clazz, int newLevel, Map<String, Integer> abilityScores) {
        return matchFeat(name, featOptions(clazz, newLevel, abilityScores)) != null;
    }

    /**
     * Why a feat that is otherwise selectable (level and class gating pass) is refused on ability
     * scores — e.g. {@code "requires Dexterity 13 and Wisdom 13"} — or {@code null} when the feat is
     * unknown, not offered at this level/class, or its ability prerequisites are met.
     */
    public static String unmetAbilityPrerequisite(String name, String clazz, int newLevel,
                                                  Map<String, Integer> abilityScores) {
        String feat = matchFeat(name, levelAndClassOptions(clazz, newLevel));
        if (feat == null || meetsAbilityPrerequisites(feat, abilityScores)) return null;
        return unmetAbilities(feat);
    }

    /** {@code "requires Dexterity 13 and Wisdom 13"} for a feat with ability prerequisites. */
    private static String unmetAbilities(String feat) {
        List<String> parts = ABILITY_PREREQUISITES.get(feat).entrySet().stream()
                .map(e -> ABILITY_NAMES.getOrDefault(e.getKey(), e.getKey()) + " " + e.getValue())
                .toList();
        return "requires " + String.join(" and ", parts);
    }

    private static boolean meetsAbilityPrerequisites(String feat, Map<String, Integer> abilityScores) {
        Map<String, Integer> required = ABILITY_PREREQUISITES.get(feat);
        if (required == null) return true;
        Map<String, Integer> scores = abilityScores == null ? Map.of() : abilityScores;
        return required.entrySet().stream()
                .allMatch(e -> scores.getOrDefault(e.getKey(), 0) >= e.getValue());
    }

    private static String matchFeat(String name, List<String> options) {
        if (name == null) return null;
        String trimmed = name.trim();
        return options.stream().filter(f -> f.equalsIgnoreCase(trimmed)).findFirst().orElse(null);
    }

    private static boolean hasFightingStyleFeature(String clazz) {
        return clazz != null && FIGHTING_STYLE_CLASSES.contains(clazz.trim().toLowerCase());
    }
}
