package com.moo.charactermanagerservice.dto;

/**
 * One line of a DM's batched renown save: move this character's score with
 * {@code factionId} by {@code delta} (any non-zero whole number; the result is
 * clamped to -100..100).
 */
public record RenownChange(Long factionId, Integer delta) {}
