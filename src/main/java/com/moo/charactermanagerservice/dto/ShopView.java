package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The shop browse snapshot, returned by {@code GET /session/{id}/shop} to the DM
 * and to targeted attendees only. {@code attendeePcIds} lets the DM's UI show
 * who is currently at the shop; players use it to confirm their character is in.
 */
public record ShopView(
        Long shopId,
        Long sessionId,
        String category,
        String settlement,
        List<Long> attendeePcIds,
        List<ShopItemView> items
) {}
