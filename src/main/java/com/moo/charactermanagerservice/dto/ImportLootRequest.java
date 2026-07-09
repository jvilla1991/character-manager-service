package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * Loot lines as pasted JSON, appended to a curated encounter's existing loot
 * (unlike {@link ImportShopRequest}, which creates a whole shop). Each line is
 * either a catalog reference ({@code key}) or a free-hand custom item
 * ({@code name} + optional {@code notes}) — exactly one of the two; {@code qty}
 * defaults to 1. {@code coinGp} (fractions allowed) adds to the encounter's
 * coin pile. Unknown catalog keys fail the whole import with a 400 listing them.
 */
public record ImportLootRequest(Double coinGp, List<Item> items) {
    public record Item(String key, String name, String notes, Integer qty) {}
}
