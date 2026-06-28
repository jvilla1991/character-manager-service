package com.moo.charactermanagerservice.dto;

import java.util.List;
import java.util.Map;

/**
 * The outcome of a successful purchase: the character's new coin purse and full
 * inventory (so the client can update the sheet without a refetch), plus the
 * total paid in copper. Coins are the five denominations {cp, sp, ep, gp, pp};
 * inventory mirrors the JSON-on-pc item shape.
 */
public record PurchaseResult(
        Map<String, Integer> coins,
        List<Map<String, Object>> inventory,
        long totalCostCp
) {}
