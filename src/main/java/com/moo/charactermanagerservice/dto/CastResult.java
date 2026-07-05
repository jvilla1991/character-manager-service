package com.moo.charactermanagerservice.dto;

import java.util.List;
import java.util.Map;

/**
 * Outcome of a spell cast for the acting player's own sheet: the updated slot
 * ledger plus the inventory (a consumed-on-cast component line may have been
 * decremented). Mirrors {@link ConsumeResult} — the session snapshot doesn't
 * carry inventory, so the actor needs the payload; everyone else sees the slot
 * change via the bumped session version. {@code warning} is non-null when the
 * cast went through despite a missing costly component (lenient campaigns).
 */
public record CastResult(
        Long pcId,
        Map<String, Object> spellSlots,
        List<Map<String, Object>> inventory,
        String warning
) {}
