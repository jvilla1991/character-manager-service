package com.moo.charactermanagerservice.dto;

/**
 * One prepped loot line on a curated encounter. {@code name} is resolved — the
 * catalog item's name for catalog lines, or the custom name — so the editor
 * renders one field. {@code custom} distinguishes the two; {@code customNotes}
 * is null for catalog lines.
 */
public record EncounterLootItemView(
        Long id,
        String catalogItemKey,
        String name,
        boolean custom,
        String customNotes,
        int qty
) {}
