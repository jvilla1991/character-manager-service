package com.moo.charactermanagerservice.dto;

/**
 * Body of {@code POST /session/{id}/enemies}. {@code armorClass} is the enemy's
 * AC — optional display/reference info (null = unknown); {@code hpMax} is
 * optional. The enemy is created with no initiative, so it sits at the bottom
 * of the order until the DM enters one.
 */
public record AddEnemyRequest(String name, Short armorClass, Short hpMax) {}
