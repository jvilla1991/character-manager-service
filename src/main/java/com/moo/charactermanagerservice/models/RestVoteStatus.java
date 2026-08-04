package com.moo.charactermanagerservice.models;

/**
 * Lifecycle of a player-initiated short-rest vote. ACTIVE is the only live
 * state; the rest are terminal. FAILED covers every "no rest" outcome — a no
 * vote, the 60-second window expiring with unanswered ballots (unanimity means
 * silence is not consent), or the DM's deny override. CANCELLED is reserved
 * for system interruption (an encounter starting mid-vote).
 */
public enum RestVoteStatus {
    ACTIVE,
    PASSED,
    FAILED,
    CANCELLED
}
