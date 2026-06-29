package com.moo.charactermanagerservice.dto;

/** DM request to change a curated line's price override (null = back to catalog price). */
public record UpdateShopItemRequest(Long priceCp) {}
