package com.moo.charactermanagerservice.dto;

/**
 * DM opens a loot pool (as an invisible draft). It starts one of three ways:
 * {@code lootId} seeds it by copying that curated loot list's lines and coin
 * pile; {@code contents} seeds it from pasted JSON — the same lines-and-coins
 * shape a curated loot list imports; neither starts it empty. Giving both is
 * an error. {@code name} labels the drop (defaults to the seeding list's name).
 */
public record OpenLootRequest(Long lootId, String name, ImportLootRequest contents) {}
