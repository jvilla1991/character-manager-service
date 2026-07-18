package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * Loot lines as pasted JSON, appended to a curated loot list's existing lines
 * ({@code coinGp}, fractions allowed, adds to its coin pile). Each line is
 * either a catalog reference ({@code key}) or a free-hand custom item
 * ({@code name} + optional {@code notes} and attributes) — exactly one of the
 * two; {@code qty} defaults to 1. The attribute fields mirror
 * {@link AddLootItemRequest} and are valid on custom lines only. Unknown
 * catalog keys fail the whole import with a 400 listing them; legacy payloads
 * (key/name/notes/qty only) import unchanged.
 */
public record ImportLootRequest(Double coinGp, List<Item> items) {
    public record Item(String key, String name, String notes, Integer qty,
                       String category, Double valueGp, Double weight,
                       String damage, String armorClass) {}
}
