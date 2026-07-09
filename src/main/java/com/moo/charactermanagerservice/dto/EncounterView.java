package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * A curated encounter with its creature lines and prepped loot (item lines plus
 * a coin pile in copper), for the DM editor.
 */
public record EncounterView(
        Long id,
        Long campaignId,
        String name,
        String notes,
        List<EncounterCreatureView> creatures,
        long lootCoinCp,
        List<EncounterLootItemView> lootItems
) {}
