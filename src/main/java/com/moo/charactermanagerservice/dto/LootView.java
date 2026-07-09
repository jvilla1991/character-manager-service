package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The session's loot pool as seen by an allowed caller (the DM at any time, or
 * a seated player once {@code dropped}). Coin amounts are copper integers; the
 * client renders them in gp.
 */
public record LootView(
        Long id,
        Long sessionId,
        String name,
        boolean dropped,
        long coinCpTotal,
        long coinCpRemaining,
        List<LootItemView> items
) {}
