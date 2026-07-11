package com.moo.charactermanagerservice.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared rules for a loot LINE — the catalog-key-or-custom-name reference shape
 * used by both curated encounter prep ({@link CuratedEncounterService}) and the
 * live session pool ({@link LootService}). One place instead of two identical
 * private copies, so the two flows can't drift: the items a loot line produces
 * are denormalized by {@link InventoryEntries}, the same helper the shop
 * purchase and DM-grant paths use.
 */
final class LootLines {

    private LootLines() {}

    /** 400 unless exactly one of catalogItemKey/customName is set and qty (if given) is ≥ 1. */
    static void validate(String catalogItemKey, String customName, Integer qty) {
        boolean hasKey = catalogItemKey != null && !catalogItemKey.isBlank();
        boolean hasName = customName != null && !customName.isBlank();
        if (hasKey == hasName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide either a catalogItemKey or a customName (not both)");
        }
        if (qty != null && qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
        }
    }

    /** The trimmed catalog key, or null for a custom line (blank counts as absent). */
    static String normalizeKey(String catalogItemKey) {
        return catalogItemKey == null || catalogItemKey.isBlank() ? null : catalogItemKey.trim();
    }
}
