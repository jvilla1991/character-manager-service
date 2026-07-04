package com.moo.charactermanagerservice.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One entry in the raw catalog browse payload (DM-grant picker). Unlike
 * {@link ShopItemView} it carries the effective {@code bulk} rating — the same
 * value {@code ShopService#newInventoryEntry} stamps on a purchase — so the
 * frontend can denormalize a granted item without re-deriving weight bands.
 * {@code category} stays the raw catalog enum (WEAPON); the frontend maps labels.
 */
public record CatalogItemView(
        String itemKey,
        String name,
        String category,
        long costCp,
        BigDecimal weight,
        BigDecimal bulk,
        Map<String, Object> details
) {}
