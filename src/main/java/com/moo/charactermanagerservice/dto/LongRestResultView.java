package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The latest long rest outcome, broadcast on the poll snapshot. {@code id}
 * changes with every new outcome, so a client announces each one once.
 * {@code outcome}: FINISHED (recovery was applied) | INTERRUPTED (the phase
 * closed and nobody recovered — the DM interrupted it or an encounter began).
 * {@code skipped} names the heroes at 0 hit points who gained nothing from a
 * finished rest. {@code createdAt} is an ISO instant so a late joiner can skip
 * a stale outcome.
 */
public record LongRestResultView(
        Long id,
        String outcome,
        List<String> skipped,
        boolean clockAdvanced,
        String createdAt
) {}
