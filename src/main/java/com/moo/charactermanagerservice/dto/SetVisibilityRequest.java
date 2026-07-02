package com.moo.charactermanagerservice.dto;

/**
 * Body of {@code PUT /session/{id}/visibility}. When {@code enemiesHidden} is
 * true, players' snapshots omit enemy combatants entirely.
 */
public record SetVisibilityRequest(Boolean enemiesHidden) {}
