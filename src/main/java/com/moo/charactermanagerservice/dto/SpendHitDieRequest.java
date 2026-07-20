package com.moo.charactermanagerservice.dto;

/** Body of {@code POST /session/{id}/hit-dice/spend} — the caller's own seated PC. */
public record SpendHitDieRequest(Long pcId) {}
