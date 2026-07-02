package com.moo.charactermanagerservice.dto;

/**
 * A player's sale: sell the entire stack at inventory position {@code index}
 * (the current index in the caller's PC.inventory array) for character
 * {@code pcId}. The caller must own the PC and the PC must be at the active shop.
 */
public record SellRequest(Long pcId, Integer index) {}
