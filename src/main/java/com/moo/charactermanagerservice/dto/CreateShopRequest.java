package com.moo.charactermanagerservice.dto;

/** DM request to create a curated shop in a campaign. */
public record CreateShopRequest(String name, String settlement) {}
