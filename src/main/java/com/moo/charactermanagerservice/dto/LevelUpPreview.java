package com.moo.charactermanagerservice.dto;

import java.util.List;
import java.util.Map;

/**
 * Read-only projection of what advancing one level would grant, computed server-side so the
 * SPA can render the deltas before the user commits.
 *
 * <p>Phase 1: deterministic gains (HP via fixed average, proficiency bonus). Phase 2: the
 * spell-slot picture — {@code spellLevel -> maxSlots} before and after — empty for non-casters.
 * Phase 3: whether a subclass choice is due at the new level ({@code subclassDue}) and the
 * selectable names ({@code subclassOptions}); the options list is empty until catalog content
 * is authored, so the SPA shows the picker only when there is something to pick.
 *
 * <p>Fighting Style: {@code fightingStyleDue} is true when the new level is the class's Fighting
 * Style level (Paladin/Ranger 2), and {@code fightingStyleOptions} then lists the selectable
 * names, sorted (the Fighting Style feats, plus {@code "Void Warrior"} for a Demon Hunter);
 * otherwise false and empty. The choice is optional in the request.
 *
 * <p>Recommended subclass: when {@code subclassDue} and the PC has a World of Warcraft class
 * specialization (e.g. a Balance druid), {@code recommendedSubclass} names the option that
 * specialization recommends (e.g. {@code "Circle of the Stars"}) so the SPA can highlight it;
 * advisory only, never enforced. Otherwise null.
 */
public record LevelUpPreview(
        int currentLevel,
        int newLevel,
        int hitDie,
        int conModifier,
        int hpGained,
        int newHpMax,
        int currentProfBonus,
        int newProfBonus,
        Map<Integer, Integer> currentSpellSlots,
        Map<Integer, Integer> newSpellSlots,
        boolean subclassDue,
        List<String> subclassOptions,
        boolean asiDue,
        List<String> featOptions,
        List<FeatureGain> featuresGained,
        int currentCantripsKnown,
        int newCantripsKnown,
        int currentSpellsKnown,
        int newSpellsKnown,
        boolean fightingStyleDue,
        List<String> fightingStyleOptions,
        String recommendedSubclass
) {
    /** Convenience constructor for a preview with no Fighting Style choice due. */
    public LevelUpPreview(int currentLevel, int newLevel, int hitDie, int conModifier, int hpGained,
                          int newHpMax, int currentProfBonus, int newProfBonus,
                          Map<Integer, Integer> currentSpellSlots, Map<Integer, Integer> newSpellSlots,
                          boolean subclassDue, List<String> subclassOptions, boolean asiDue,
                          List<String> featOptions, List<FeatureGain> featuresGained,
                          int currentCantripsKnown, int newCantripsKnown,
                          int currentSpellsKnown, int newSpellsKnown) {
        this(currentLevel, newLevel, hitDie, conModifier, hpGained, newHpMax, currentProfBonus,
                newProfBonus, currentSpellSlots, newSpellSlots, subclassDue, subclassOptions, asiDue,
                featOptions, featuresGained, currentCantripsKnown, newCantripsKnown,
                currentSpellsKnown, newSpellsKnown, false, List.of(), null);
    }
}
