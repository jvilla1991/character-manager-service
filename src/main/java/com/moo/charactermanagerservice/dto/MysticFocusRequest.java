package com.moo.charactermanagerservice.dto;

/**
 * One Mystic Focus conversion of a Warrior of the Mystic Arts monk
 * ({@code POST /api/v1/pc/{id}/mystic-focus}).
 *
 * @param action    {@code "SLOT_TO_FOCUS"} (spend a spell slot, regain Focus Points) or
 *                  {@code "FOCUS_TO_SLOT"} (spend Focus Points, recover a spell slot)
 * @param slotLevel the spell level of the slot spent or recovered, 1-4
 */
public record MysticFocusRequest(String action, Integer slotLevel) {}
