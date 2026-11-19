package com.moo.charactermanagerservice.dto;

/**
 * DM options for the open long rest phase. Either field may be null to leave
 * it unchanged. {@code advanceClock}: move the campaign clock to the next
 * morning on finish. {@code undisturbed}: survival fatigue relief of 3 (true)
 * or 1 (false).
 */
public record LongRestOptionsRequest(Boolean advanceClock, Boolean undisturbed) {}
