package com.moo.charactermanagerservice.dto;

import java.time.Instant;
import java.util.List;

/** One row in the Roll Log panel — the poll-embedded, per-viewer-filtered projection. */
public record SessionRollView(
        Long rollId,
        Long participantId,
        String rollerName,
        boolean mine,
        List<DieGroupView> groups,
        Integer grandTotal,
        Instant createdAt
) {
    public record DieGroupView(Integer sides, List<Integer> rolls, Integer subtotal) {}
}
