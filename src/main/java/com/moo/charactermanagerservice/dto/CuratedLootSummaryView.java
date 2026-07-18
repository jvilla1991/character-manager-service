package com.moo.charactermanagerservice.dto;

/** Row for the campaign dashboard's curated-loot list (no line detail). */
public record CuratedLootSummaryView(
        Long id,
        Long campaignId,
        String name,
        String notes,
        long coinCp,
        long itemCount
) {}
