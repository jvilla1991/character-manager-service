package com.moo.charactermanagerservice.dto;

/**
 * Body of {@code PUT /session/{id}/visibility}. When {@code enemiesHidden} is
 * true, players' snapshots omit enemy combatants entirely. When enemies are
 * visible, {@code enemyHpHidden} = true keeps the rows but nulls their HP
 * server-side ("players see enemies, hide health"). {@code enemyHpHidden} is
 * optional for backward compatibility — a null leaves the stored value
 * unchanged, so old clients that only send {@code enemiesHidden} keep working.
 */
public record SetVisibilityRequest(Boolean enemiesHidden, Boolean enemyHpHidden) {}
