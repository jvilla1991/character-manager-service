package com.moo.charactermanagerservice.dto;

/**
 * A player's purchase: buy {@code qty} of the catalog item {@code itemKey} for
 * character {@code pcId}. The caller must own the PC and the PC must be at the
 * active shop.
 */
public record PurchaseRequest(Long pcId, String itemKey, Integer qty) {}
