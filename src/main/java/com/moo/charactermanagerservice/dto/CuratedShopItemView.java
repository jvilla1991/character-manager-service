package com.moo.charactermanagerservice.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A curated shop line, resolved against the catalog for the DM editor:
 * {@code priceOverrideCp} is the DM's override (null = none) and
 * {@code effectiveCostCp} is what buyers pay (override, else catalog price).
 */
public record CuratedShopItemView(
        Long id,
        String catalogItemKey,
        String name,
        String category,
        Long priceOverrideCp,
        long effectiveCostCp,
        BigDecimal weight,
        Map<String, Object> details
) {}
