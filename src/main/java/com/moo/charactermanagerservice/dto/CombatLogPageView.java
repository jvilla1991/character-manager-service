package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * One page of a session's combat log, newest first — the payload of
 * {@code GET /api/v1/session/{id}/combat-log}. {@code page} is zero-based;
 * {@code total} counts every row in the log, so the client can work out how
 * many pages there are.
 */
public record CombatLogPageView(
        List<CombatLogEntryView> entries,
        int page,
        int size,
        long total
) {}
