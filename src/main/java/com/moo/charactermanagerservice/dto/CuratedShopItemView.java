package com.moo.charactermanagerservice.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A curated shop line for the DM editor. A catalog line is resolved against the
 * catalog: {@code priceOverrideCp} is the DM's override (null = none) and
 * {@code effectiveCostCp} is what buyers pay (override, else catalog price). A
 * custom line ({@code custom} true, {@code catalogItemKey} null) carries the
 * DM's own stats; both prices are its one price, {@code details} holds its
 * damage / armor class / notes the way a catalog entry's would, and
 * {@code category} is the catalog-style code (WEAPON, TREASURE, …).
 */
public record CuratedShopItemView(
        Long id,
        String catalogItemKey,
        String name,
        String category,
        Long priceOverrideCp,
        long effectiveCostCp,
        BigDecimal weight,
        Map<String, Object> details,
        boolean custom,
        String notes,
        BigDecimal bulk
) {}
