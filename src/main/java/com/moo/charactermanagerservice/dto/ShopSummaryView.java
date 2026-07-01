package com.moo.charactermanagerservice.dto;

/** A curated shop in the DM's list view (no item detail). */
public record ShopSummaryView(Long id, Long campaignId, String name, String settlement, int itemCount) {}
