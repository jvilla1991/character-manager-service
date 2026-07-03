package com.moo.charactermanagerservice.dto;

/** One creature line in a curated encounter, for the DM editor. */
public record EncounterCreatureView(Long id, String name, short dexModifier, Short hpMax, int quantity) {}
