package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The session's loot pool as seen by an allowed caller (the DM at any time, or
 * a seated player once dropped). Coin amounts are copper integers; the client
 * renders them in gp.
 *
 * <p>Party split: {@code phase} is DRAFT (DM only), SPLITTING (players are
 * dividing it) or VOTING (the split is locked while the party votes). {@code
 * coinCpRemaining} and each item's {@code qtyRemaining} are what is still in
 * the pile. {@code recipients} are the seated characters loot can go to;
 * {@code allocations} / {@code coinShares} say who has what; {@code ballots}
 * is empty unless VOTING. The split passes with {@code majorityNeeded} ✓
 * votes once every ballot is settled. {@code mine} flags mark the caller's
 * own character.
 */
public record LootView(
        Long id,
        Long sessionId,
        String name,
        boolean dropped,
        long coinCpTotal,
        long coinCpRemaining,
        List<LootItemView> items,
        String phase,
        List<Recipient> recipients,
        List<Allocation> allocations,
        List<CoinShare> coinShares,
        List<Ballot> ballots,
        int majorityNeeded
) {
    public record Recipient(Long participantId, Long pcId, String name, boolean mine) {}

    public record Allocation(Long participantId, Long lootItemId, int qty) {}

    public record CoinShare(Long participantId, long coinCp) {}

    public record Ballot(Long participantId, String name, Boolean vote, Boolean pvp, boolean mine) {}
}
