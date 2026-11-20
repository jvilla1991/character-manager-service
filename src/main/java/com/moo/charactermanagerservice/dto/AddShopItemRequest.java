package com.moo.charactermanagerservice.dto;

/**
 * Add one line to a curated shop. Either a catalog item ({@code catalogItemKey},
 * with an optional {@code priceCp} override) or a custom item the DM wrote out
 * ({@code customName} plus its own stats) — exactly one of the two. For a
 * custom item {@code priceCp} is the price itself (absent = free); the stat
 * fields are rejected on a catalog line, whose stats come from the catalog.
 */
public record AddShopItemRequest(
        String catalogItemKey,
        Long priceCp,
        String customName,
        String customNotes,
        String category,
        Double weight,
        Double bulk,
        String damage,
        String armorClass
) {
    /** A catalog line. */
    public AddShopItemRequest(String catalogItemKey, Long priceCp) {
        this(catalogItemKey, priceCp, null, null, null, null, null, null, null);
    }
}
