package com.moo.charactermanagerservice.dto;

import java.util.List;
import java.util.Map;

/**
 * The outcome of a successful loot claim: the character's new purse and full
 * inventory (so the client updates the sheet without a refetch, mirroring
 * {@link PurchaseResult}), plus the refreshed pool so remaining counts render
 * immediately.
 */
public record ClaimResult(
        Map<String, Integer> coins,
        List<Map<String, Object>> inventory,
        LootView loot
) {}
