package com.moo.charactermanagerservice.dto;

/**
 * Update a loot line's quantity, and (for custom lines) its name/notes.
 * Catalog lines ignore the name/notes fields — the catalog is authoritative.
 */
public record UpdateLootItemRequest(Integer qty, String customName, String customNotes) {}
