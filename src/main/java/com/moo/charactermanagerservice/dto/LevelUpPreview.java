package com.moo.charactermanagerservice.dto;

/**
 * Read-only projection of what advancing one level would grant, computed server-side so the
 * SPA can render the deltas before the user commits. No persistence and no player choices —
 * Phase 1 covers the deterministic gains (HP via fixed average, proficiency bonus). Later
 * phases that introduce choices (subclass, ASI/feat, spells) add a separate request contract.
 */
public record LevelUpPreview(
        int currentLevel,
        int newLevel,
        int hitDie,
        int conModifier,
        int hpGained,
        int newHpMax,
        int currentProfBonus,
        int newProfBonus
) {}
