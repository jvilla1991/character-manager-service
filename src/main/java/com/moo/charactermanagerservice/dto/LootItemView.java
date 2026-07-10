package com.moo.charactermanagerservice.dto;

/**
 * One claimable line in the session loot pool. {@code name} is resolved
 * (catalog name or custom name); {@code qtyRemaining} is what's left to claim —
 * the DM sees both, players use remaining for their steppers.
 */
public record LootItemView(
        Long id,
        String catalogItemKey,
        String name,
        boolean custom,
        String customNotes,
        int qty,
        int qtyRemaining
) {}
