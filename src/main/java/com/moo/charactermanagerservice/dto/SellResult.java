package com.moo.charactermanagerservice.dto;

import java.util.List;
import java.util.Map;

/**
 * The outcome of a successful sale: the character's new coin purse and full
 * inventory (line removed, so the client can update the sheet without a
 * refetch), plus the total gained in copper.
 */
public record SellResult(
        Map<String, Integer> coins,
        List<Map<String, Object>> inventory,
        long totalGainCp
) {}
