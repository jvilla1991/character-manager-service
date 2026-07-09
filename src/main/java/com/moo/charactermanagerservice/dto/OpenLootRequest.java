package com.moo.charactermanagerservice.dto;

/**
 * DM opens a loot pool (as an invisible draft). {@code encounterId} seeds it by
 * copying that curated encounter's prepped loot and coin pile; null starts
 * empty. {@code name} labels the drop (defaults to the seeding encounter's name).
 */
public record OpenLootRequest(Long encounterId, String name) {}
