package com.moo.charactermanagerservice.dto;

import java.util.List;

/** A curated shop with its resolved lines, for the DM editor. */
public record CuratedShopView(
        Long id,
        Long campaignId,
        String name,
        String settlement,
        List<CuratedShopItemView> items
) {}
