package com.moo.charactermanagerservice.dto;

import java.math.BigDecimal;

/**
 * One prepped line in a curated loot list. {@code name} is resolved — the
 * catalog item's name for catalog lines, or the custom name — so the editor
 * renders one field; {@code custom} distinguishes the two. The attribute fields
 * (category/unitCostCp/weight/damage/armorClass) are set on custom lines only —
 * catalog lines take their stats from the catalog at claim time.
 */
public record CuratedLootItemView(
        Long id,
        String catalogItemKey,
        String name,
        boolean custom,
        String customNotes,
        String category,
        Long unitCostCp,
        BigDecimal weight,
        String damage,
        String armorClass,
        int qty
) {}
