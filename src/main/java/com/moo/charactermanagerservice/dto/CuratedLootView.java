package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * A curated loot list with its resolved lines and coin pile (in copper), for
 * the DM editor on the campaign dashboard and the session-mode drop picker.
 */
public record CuratedLootView(
        Long id,
        Long campaignId,
        String name,
        String notes,
        long coinCp,
        List<CuratedLootItemView> items
) {}
