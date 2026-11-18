package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * The short-rest vote as it rides the poll snapshot — broadcast to every
 * viewer (vote statuses are public at the table by design; a client finds its
 * own ballot by participant id). {@code expiresAt} is the server-authoritative
 * ISO instant the 60-second window closes: clients render a countdown from it
 * rather than ticking locally, so a backgrounded tab snaps correct on return.
 * A terminal vote (PASSED/FAILED/CANCELLED) stays in the snapshot until the
 * next vote replaces it, letting clients announce the outcome.
 * {@code restType} is SHORT or LONG — a passed LONG vote opens the long rest
 * phase ({@link LongRestView}) instead of the short-rest window.
 */
public record RestVoteView(
        Long voteId,
        String status,
        String restType,
        Long initiatorParticipantId,
        String initiatorName,
        String expiresAt,
        List<BallotView> votes
) {
    /** {@code vote} null = not yet voted (the modal's yellow "?"). */
    public record BallotView(Long participantId, String displayName, Boolean vote) {}
}
