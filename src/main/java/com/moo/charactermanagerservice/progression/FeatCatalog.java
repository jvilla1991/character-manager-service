package com.moo.charactermanagerservice.progression;

import java.util.ArrayList;
import java.util.List;
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
