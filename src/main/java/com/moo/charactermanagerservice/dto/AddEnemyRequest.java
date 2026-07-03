package com.moo.charactermanagerservice.dto;

/**
 * Body of {@code POST /session/{id}/enemies}. The DM calculates and enters the
 * enemy's DEX modifier (the initiative tie-breaker); {@code hpMax} is optional.
 * The enemy is created with no initiative, so it sits at the bottom of the
 * order until the DM enters one.
 */
public record AddEnemyRequest(String name, Short dexModifier, Short hpMax) {}
