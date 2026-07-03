package com.moo.charactermanagerservice.dto;

/** Body of {@code PUT /encounters/{id}/creatures/{creatureId}}. */
public record UpdateCreatureRequest(String name, Short dexModifier, Short hpMax, Integer quantity) {}
