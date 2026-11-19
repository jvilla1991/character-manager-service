package com.moo.charactermanagerservice.dto;

/**
 * Give spell slots back outside a rest
 * ({@code POST /api/v1/pc/{id}/spell-slots/restore}): {@code {"level": 3}}
 * restores one slot of that spell level, {@code {"all": true}} every spent
 * slot. {@code all} wins when both are sent.
 */
public record RestoreSpellSlotsRequest(Integer level, Boolean all) {}
