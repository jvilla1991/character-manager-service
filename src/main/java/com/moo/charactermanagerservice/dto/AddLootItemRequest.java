package com.moo.charactermanagerservice.dto;

/**
 * Add one loot line — exactly one of {@code catalogItemKey} (an SRD catalog
 * item) or {@code customName} (a free-hand item, with optional notes) must be
 * set. Shared by the curated-encounter editor and the live session pool.
 */
public record AddLootItemRequest(String catalogItemKey, String customName, String customNotes, Integer qty) {}
