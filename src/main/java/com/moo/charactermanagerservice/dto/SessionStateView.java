package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The full, server-authoritative snapshot of a live session — the payload of the
 * poll endpoint {@code GET /api/v1/session/{id}/state}. Clients render whatever
 * this says and never compute turn order themselves. {@code version} lets a
 * poller skip re-rendering when nothing has changed; {@code dm} tells the caller
 * whether they are the session's DM (and so may use DM controls).
 * {@code activeParticipantId} is the stable turn pointer — the participant whose
 * turn it is — and is null unless the encounter is ACTIVE.
 *
 * <p>The {@code shop*} fields are the targeted-sync signal for the shopping
 * feature: {@code shopOpen} is true when a shop is active in the session, and
 * {@code shopForMe} is true only for the DM or a targeted attendee — that caller
 * then fetches the full catalog from {@code GET /session/{id}/shop}. Non-targeted
 * players see {@code shopForMe = false} and render nothing.
 *
 * <p>{@code myXp} is deliberately caller-scoped rather than living on
 * {@link ParticipantView} (which is broadcast to every participant): it carries
 * the requester's own seated PC's current XP total so their sheet can update
 * live, without exposing teammates' XP to each other. Null for the DM or for a
 * player with no seated PC in this session.
 */
public record SessionStateView(
        Long sessionId,
        Long campaignId,
        String status,
        Short round,
        Long activeParticipantId,
        Long version,
        boolean dm,
        boolean shopOpen,
        boolean shopForMe,
        String shopCategory,
        Integer myXp,
        List<ParticipantView> participants
) {}
