package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The latest loot-split outcome, broadcast on the poll snapshot. {@code id}
 * changes with every new outcome, so a client announces each one once.
 * {@code outcome}: APPROVED | PVP | NO_MAJORITY | DM_APPROVED | DM_CANCELLED.
 * {@code createdAt} is an ISO instant so a client that joins late can skip
 * announcing a stale outcome.
 */
public record LootResultView(
        Long id,
        String outcome,
        String lootName,
        List<String> pvpCallers,
        int agreeCount,
        int objectCount,
        String createdAt
) {}
