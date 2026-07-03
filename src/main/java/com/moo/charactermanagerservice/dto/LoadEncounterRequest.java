package com.moo.charactermanagerservice.dto;

/** Body of {@code POST /session/{id}/encounter/load} — which curated encounter to load. */
public record LoadEncounterRequest(Long encounterId) {}
