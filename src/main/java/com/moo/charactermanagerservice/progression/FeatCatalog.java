package com.moo.charactermanagerservice.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selectable feats (D&D 5e 2024) — the feat option at an Ability Score Improvement level.
 *
 * <p>This is the server-authoritative source of truth for <em>which</em> feats may be chosen on
 * level-up; it holds names only. Descriptions are presentation and live in the frontend.
 *
 * <p>Three 2024 PHB categories are selectable, with prerequisite gating:
 * <ul>
 *   <li><b>General feats</b> — always offered at an ASI level.</li>
 *   <li><b>Fighting Style feats</b> — offered only to classes with the Fighting Style class
 *       feature (Fighter, Paladin, Ranger).</li>
 *   <li><b>Epic Boon feats</b> — offered only at level 19+.</li>
 * </ul>
 *
 * <p>Origin feats (Alert, Tough, Lucky, Magic Initiate, …) are deliberately excluded: those are
 * granted at level 1 by a background, not chosen at an ASI level.
 *
 * <p>Also home to the <b>Fighting Style class-feature choice</b> made on level-up at the class's
 * Fighting Style level (Paladin and Ranger at 2; a Fighter's is at level 1, i.e. creation): the
 * Fighting Style feats, plus {@value #VOID_WARRIOR} for a Demon Hunter ranger only.
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
     * unless the choice is due; otherwise the Fighting Style feats, plus {@value #VOID_WARRIOR}
     * for a ranger with the Demon Hunter hero subclass.
     */
    public static List<String> fightingStyleOptions(String clazz, String subclass, int newLevel) {
        if (!fightingStyleDue(clazz, newLevel)) return List.of();
        List<String> options = new ArrayList<>(FIGHTING_STYLE_FEATS);
        if (ClassProgression.heroSubclassAllowedFor(subclass, clazz) && ClassProgression.isDemonHunter(subclass)) {
            options.add(VOID_WARRIOR);
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
     * Every feat selectable by the given class at the given (new) level, sorted: General feats
     * always, Fighting Style feats when the class has the Fighting Style feature, Epic Boons at
     * level 19+.
     */
    public static List<String> featOptions(String clazz, int newLevel) {
        List<String> options = new ArrayList<>(GENERAL_FEATS);
        if (hasFightingStyleFeature(clazz)) {
            options.addAll(FIGHTING_STYLE_FEATS);
        }
        if (newLevel >= 19) {
            options.addAll(EPIC_BOON_FEATS);
        }
        return options.stream().sorted().toList();
    }

    /**
     * Whether the given name is selectable by the given class at the given (new) level
     * (case-insensitive), applying the same gating as {@link #featOptions(String, int)}.
     */
    public static boolean isValidFeat(String name, String clazz, int newLevel) {
        if (name == null) return false;
        String trimmed = name.trim();
        return featOptions(clazz, newLevel).stream().anyMatch(f -> f.equalsIgnoreCase(trimmed));
    }

    private static boolean hasFightingStyleFeature(String clazz) {
        return clazz != null && FIGHTING_STYLE_CLASSES.contains(clazz.trim().toLowerCase());
    }
}
