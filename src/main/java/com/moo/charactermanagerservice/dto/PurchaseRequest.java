package com.moo.charactermanagerservice.dto;

/**
 * A player's purchase: buy {@code qty} of an item for character {@code pcId}.
 * The item is named by {@code shopItemId} (the curated shop line — the only way
 * to name a custom item) or by {@code itemKey} (a catalog item; what a standard
 * shop uses). The caller must own the PC and the PC must be at the active shop.
 */
public record PurchaseRequest(Long pcId, String itemKey, Integer qty, Long shopItemId) {}
