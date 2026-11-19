package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * A character's spell slots after one was spent or restored by hand
 * ({@code POST /api/v1/pc/{id}/spell-slots/spend} and {@code .../restore}):
 * {@code {"1":{"max":4,"used":1}}}, keyed by spell level. The slot half of
 * {@link CastResult} — everyone else at the table sees the change through the
 * bumped session version.
 */
public record SpellSlotResult(
        Long pcId,
        Map<String, Object> spellSlots
) {}
