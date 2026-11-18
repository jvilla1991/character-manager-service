package com.moo.charactermanagerservice.dto;

/**
 * A player's sale: sell {@code qty} units (null = the entire stack) of the line at inventory position {@code index}
 * (the current index in the caller's PC.inventory array) for character
 * {@code pcId}. The caller must own the PC and the PC must be at the active shop.
 */
public record SellRequest(Long pcId, Integer index, Integer qty) {}
