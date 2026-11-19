package com.moo.charactermanagerservice.dto;

/**
 * Spend one spell slot by hand ({@code POST /api/v1/pc/{id}/spell-slots/spend}).
 * {@code level} is the spell level of the slot, 1-9.
 */
public record SpendSpellSlotRequest(Integer level) {}
