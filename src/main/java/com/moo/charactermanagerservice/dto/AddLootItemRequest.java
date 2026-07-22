package com.moo.charactermanagerservice.dto;

/**
 * Add one loot line — exactly one of {@code catalogItemKey} (an SRD catalog
 * item) or {@code customName} (a free-hand item) must be set. Shared by the
 * curated-loot editor and the live session pool. The attribute fields apply to
 * custom lines only (a catalog line with any of them is rejected — the catalog
 * is authoritative there): {@code category} is the lowercase inventory label
 * (weapon|armor|material-component|gear|transport), {@code valueGp} the value in gold
 * (stored as copper), {@code weight} in pounds, {@code damage} for weapons and
 * {@code armorClass} for armor.
 */
public record AddLootItemRequest(
        String catalogItemKey,
        String customName,
        String customNotes,
        Integer qty,
        String category,
        Double valueGp,
        Double weight,
        String damage,
        String armorClass
) {}
