package com.moo.charactermanagerservice.dto;

import java.math.BigDecimal;

/**
 * One claimable line in the session loot pool. {@code name} is resolved
 * (catalog name or custom name); {@code qtyRemaining} is what's left to claim —
 * the DM sees both, players use remaining for their steppers. The attribute
 * fields (category/unitCostCp/weight/damage/armorClass) are the custom line's
 * stats, copied from the curated list; null on catalog lines and legacy rows.
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
        int qtyRemaining
) {}
