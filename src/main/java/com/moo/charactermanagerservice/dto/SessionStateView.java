package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The full, server-authoritative snapshot of a live session — the payload of the
 * poll endpoint {@code GET /api/v1/session/{id}/state}. Clients render whatever
 * this says and never compute turn order themselves. {@code version} lets a
 * poller skip re-rendering when nothing has changed; {@code dm} tells the caller
 * whether they are the session's DM (and so may use DM controls).
 */
public record SessionStateView(
        Long sessionId,
        Long campaignId,
        String status,
        Short round,
        Short currentTurnIndex,
        Long version,
        boolean dm,
        List<ParticipantView> participants
) {}
