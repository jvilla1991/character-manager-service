package com.moo.charactermanagerservice.dto;

import java.time.Instant;

/**
 * One row of the DM's Combat Log panel. {@code type} is the
 * {@code CombatLogType} name (DAMAGE, HEALING, CONDITION, HIT_POINTS);
 * {@code round} is the encounter round the change happened in, or null when
 * it happened outside an encounter.
 */
public record CombatLogEntryView(
        Long id,
        String type,
        String subject,
        String description,
        Short round,
        Instant createdAt
) {}
