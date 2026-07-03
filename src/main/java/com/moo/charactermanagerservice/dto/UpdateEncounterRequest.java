package com.moo.charactermanagerservice.dto;

/** Body of {@code PUT /encounters/{id}} — rename / re-note a curated encounter. */
public record UpdateEncounterRequest(String name, String notes) {}
