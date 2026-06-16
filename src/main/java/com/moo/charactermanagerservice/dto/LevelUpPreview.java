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
        List<String> subclassOptions
) {}
