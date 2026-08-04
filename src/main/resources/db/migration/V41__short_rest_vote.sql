-- Player-initiated short-rest vote (5.5e table culture: the party, not just
-- the DM, can call a rest). One vote may be ACTIVE per session (partial unique
-- index); it resolves within its 60-second window — unanimously yes → the
-- session's short_rest_open window opens. Ephemeral like session_roll: rows are
-- purged when the session ends; ON DELETE CASCADE is the backstop for the case
-- a combat_session row itself is hard-deleted via the campaign cascade chain.
CREATE TABLE IF NOT EXISTS session_rest_vote (
    id                       BIGSERIAL PRIMARY KEY,
    session_id               BIGINT NOT NULL REFERENCES combat_session (id) ON DELETE CASCADE,
    initiator_participant_id BIGINT REFERENCES session_participant (id) ON DELETE SET NULL,
    initiator_name           TEXT NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|PASSED|FAILED|CANCELLED
    expires_at               TIMESTAMPTZ NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rest_vote_one_active
    ON session_rest_vote (session_id) WHERE status = 'ACTIVE';

-- One ballot per seated PC participant, snapshotted when the vote opens
-- (ad-hoc NPCs and the DM never hold ballots). vote NULL = not yet voted.
-- CASCADE from session_participant means a player who leaves mid-vote drops
-- out of the unanimity set automatically.
CREATE TABLE IF NOT EXISTS session_rest_vote_ballot (
    id             BIGSERIAL PRIMARY KEY,
    vote_id        BIGINT NOT NULL REFERENCES session_rest_vote (id) ON DELETE CASCADE,
    participant_id BIGINT NOT NULL REFERENCES session_participant (id) ON DELETE CASCADE,
    display_name   TEXT NOT NULL,
    vote           BOOLEAN,
    UNIQUE (vote_id, participant_id)
);

CREATE INDEX IF NOT EXISTS idx_rest_vote_ballot_vote ON session_rest_vote_ballot (vote_id);
