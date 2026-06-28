package com.moo.charactermanagerservice.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One purchasable line in the shop browse payload. {@code costCp} is the price
 * in copper; the frontend renders gp/sp/cp from it. {@code details} is the
 * catalog entry's category-specific JSON, parsed to a map (damage, properties,
 * …). {@code stock} is null for standard shops (unlimited in Phase 1); a finite
 * count arrives with curated shops.
 */
public record ShopItemView(
        String itemKey,
        String name,
        String category,
        long costCp,
        BigDecimal weight,
        Map<String, Object> details,
        Integer stock
) {}
