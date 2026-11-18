package com.moo.charactermanagerservice.dto;

/**
 * A player's ballot on the locked split. {@code vote}: true = ✓, false = ✗,
 * null = take the ballot back. {@code pvp} is read only on a ✗: null = still
 * deciding, true = PvP (cancels the loot), false = just object.
 */
public record CastLootVoteRequest(Boolean vote, Boolean pvp) {}
