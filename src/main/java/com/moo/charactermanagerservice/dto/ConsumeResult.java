package com.moo.charactermanagerservice.dto;

import java.util.List;
import java.util.Map;

/**
 * Outcome of a survival consume action for the acting player's own sheet:
 * the updated stages plus the inventory (a ration/waterskin line may have been
 * decremented). Mirrors the sell flow's result — the session snapshot doesn't
 * carry inventory, so the actor needs the payload; everyone else sees the
 * survival change via the bumped session version.
 */
public record ConsumeResult(
        Long pcId,
        Map<String, Object> survival,
        List<Map<String, Object>> inventory
) {}
