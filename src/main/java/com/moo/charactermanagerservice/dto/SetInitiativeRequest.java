package com.moo.charactermanagerservice.dto;

/** Body for PUT /api/v1/session/{id}/participants/{pid}/initiative — the DM
 *  enters a combatant's rolled initiative total. */
public record SetInitiativeRequest(Short value) {}
