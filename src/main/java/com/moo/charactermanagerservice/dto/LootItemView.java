package com.moo.charactermanagerservice.dto;

import java.math.BigDecimal;

/**
 * One line in the session loot pool. {@code name} is resolved (catalog name or
 * custom name). {@code qtyRemaining} is how many are still in the pile — not
 * yet allocated to a character in the party split. The attribute fields
 * (category/unitCostCp/weight/damage/armorClass) are the custom line's stats,
 * copied from the curated list; null on catalog lines and legacy rows.
 * {@code valueCp} is the per-unit value players weigh shares by (the catalog
 * price, or the custom line's value); null when unknown.
 */
public record LootItemView(
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
        int qty,
        int qtyRemaining,
        Long valueCp,
        BigDecimal bulk
) {}
