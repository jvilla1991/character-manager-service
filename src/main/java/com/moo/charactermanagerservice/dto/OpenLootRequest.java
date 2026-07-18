package com.moo.charactermanagerservice.dto;

/**
 * DM opens a loot pool (as an invisible draft). {@code lootId} seeds it by
 * copying that curated loot list's lines and coin pile; null starts empty.
 * {@code name} labels the drop (defaults to the seeding list's name).
 */
public record OpenLootRequest(Long lootId, String name) {}
