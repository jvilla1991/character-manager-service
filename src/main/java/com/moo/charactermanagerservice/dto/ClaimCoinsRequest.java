package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * A player takes coins from the loot pile — an amount expressed as denominations
 * ({cp, sp, ep, gp, pp}), summed server-side to copper.
 */
public record ClaimCoinsRequest(Long pcId, Map<String, Integer> coins) {}
