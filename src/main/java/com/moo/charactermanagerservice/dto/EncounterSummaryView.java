package com.moo.charactermanagerservice.dto;

/** A curated encounter in the DM's list view (no creature detail). */
public record EncounterSummaryView(Long id, Long campaignId, String name, String notes, int creatureCount) {}
