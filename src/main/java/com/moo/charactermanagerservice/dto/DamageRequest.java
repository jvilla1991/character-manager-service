package com.moo.charactermanagerservice.dto;

/** Body for POST /api/v1/session/{id}/participants/{pid}/damage — the DM applies
 *  damage (positive) or healing (negative) to a combatant. */
public record DamageRequest(Integer amount) {}
