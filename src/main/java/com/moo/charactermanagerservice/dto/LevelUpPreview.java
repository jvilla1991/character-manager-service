package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * Read-only projection of what advancing one level would grant, computed server-side so the
 * SPA can render the deltas before the user commits. No persistence and no player choices.
 *
 * <p>Phase 1: deterministic gains (HP via fixed average, proficiency bonus). Phase 2 adds the
 * spell-slot picture — {@code spellLevel -> maxSlots} before and after the level — which is
 * empty for non-casters. Later phases that introduce choices (subclass, ASI/feat, spells) add
 * a separate request contract.
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
        Map<Integer, Integer> newSpellSlots
) {}
