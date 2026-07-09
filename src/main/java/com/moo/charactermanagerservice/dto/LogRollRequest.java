package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * Body of POST /api/v1/session/{id}/participants/{participantId}/rolls. The
 * client has already rolled (the dice-flight animation reveals pre-computed
 * results) — this just logs them. Server trusts the client's RNG/results; it
 * validates shape (non-empty, known die sizes, roll values in range) but does
 * not re-roll. `groups` mirrors the modal's `breakdown` getter: one entry per
 * die type actually rolled.
 */
public record LogRollRequest(List<DieGroup> groups) {
    public record DieGroup(Integer sides, List<Integer> rolls) {}
}
