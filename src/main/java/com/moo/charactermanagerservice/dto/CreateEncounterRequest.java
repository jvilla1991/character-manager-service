package com.moo.charactermanagerservice.dto;

/** Body of {@code POST /campaign/{campaignId}/encounters}. */
public record CreateEncounterRequest(String name, String notes) {}
