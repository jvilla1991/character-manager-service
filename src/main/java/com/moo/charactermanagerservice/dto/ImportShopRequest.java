package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * A whole curated shop as pasted JSON: name, optional settlement, and catalog
 * lines by item key with an optional price override in gold (fractions allowed
 * — 0.5 gp = 5 sp). Every key must exist in the SRD catalog; unknown keys fail
 * the whole import with a 400 listing them.
 */
public record ImportShopRequest(String name, String settlement, List<Item> items) {

    /**
     * One pasted line: a catalog item ({@code key}, optional {@code priceGp}
     * override) or a custom item ({@code name}, a required {@code priceGp}, and
     * its own stats) — exactly one of the two.
     */
    public record Item(String key, Double priceGp, String name, String notes, String category,
                       Double weight, Double bulk, String damage, String armorClass) {
        /** A catalog line. */
        public Item(String key, Double priceGp) {
            this(key, priceGp, null, null, null, null, null, null, null);
        }
    }
}
