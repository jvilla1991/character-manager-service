package com.moo.charactermanagerservice.dto;

import java.time.Instant;
import java.util.Map;

/**
 * A follower as the client sees it: the stored stat block parsed back into a
 * JSON object so the SPA never has to double-decode a string. {@code kind} is
 * the {@code FollowerKind} name (DM_GRANT, SUMMON, FAMILIAR, COMPANION).
 */
public record FollowerView(
        Long id,
        Long pcId,
        String name,
        Map<String, Object> statBlock,
        Instant createdAt,
        String kind
) {}
