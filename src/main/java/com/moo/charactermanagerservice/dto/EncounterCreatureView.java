package com.moo.charactermanagerservice.dto;

/** One creature line in a curated encounter, for the DM editor. {@code armorClass} null = unknown AC. */
public record EncounterCreatureView(Long id, String name, Short armorClass, Short hpMax, int quantity) {}
