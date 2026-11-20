package com.moo.charactermanagerservice.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One purchasable line in the shop browse payload. {@code costCp} is the price
 * in copper; the frontend renders gp/sp/cp from it. {@code details} is the
 * catalog entry's category-specific JSON, parsed to a map (damage, properties,
 * …). {@code stock} is null for standard shops (unlimited in Phase 1); a finite
 * count arrives with curated shops.
 *
 * <p>{@code shopItemId} is the curated shop line behind the entry (null in a
 * standard shop) — the handle a purchase sends back. A custom line
 * ({@code custom} true) has no {@code itemKey}; its stats sit in {@code details}
 * (damage, armorClass, notes) like a catalog entry's.
 */
public record ShopItemView(
        String itemKey,
        String name,
        String category,
        long costCp,
        BigDecimal weight,
        Map<String, Object> details,
        Integer stock,
        Long shopItemId,
        boolean custom
) {}
