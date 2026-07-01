package com.moo.charactermanagerservice.dto;

/** DM request to rename / relabel a curated shop. */
public record UpdateShopRequest(String name, String settlement) {}
