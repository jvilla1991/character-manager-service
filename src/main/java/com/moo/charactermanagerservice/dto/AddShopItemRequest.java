package com.moo.charactermanagerservice.dto;

/**
 * DM request to add a catalog item to a curated shop. {@code priceCp} is an
 * optional override in copper; null inherits the catalog price.
 */
public record AddShopItemRequest(String catalogItemKey, Long priceCp) {}
