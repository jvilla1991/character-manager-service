package com.moo.charactermanagerservice.dto;

/**
 * Body of {@code POST /encounters/{id}/creatures}. Free-hand enemy data mirroring
 * the in-session {@link AddEnemyRequest}, plus a {@code quantity} that expands into
 * numbered combatants on load. {@code hpMax} is optional; {@code quantity} null
 * defaults to 1.
 */
public record AddCreatureRequest(String name, Short dexModifier, Short hpMax, Integer quantity) {}
