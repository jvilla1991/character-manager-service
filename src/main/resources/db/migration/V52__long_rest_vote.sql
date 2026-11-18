-- Long rest. The player-initiated rest vote (V41) now carries a rest type, and
-- a LONG vote that passes (or the DM's Long Rest button) opens a "long rest
-- phase" on the session instead of applying anything: each seated player
-- reviews what the rest restores and marks Ready, and recovery lands for the
-- whole party in one transaction when the DM finishes it or the last player
-- readies up.

-- SHORT is the default so votes opened by older clients keep their meaning.
ALTER TABLE session_rest_vote
    ADD COLUMN IF NOT EXISTS rest_type TEXT NOT NULL DEFAULT 'SHORT'; -- SHORT|LONG

-- The phase itself is session state, like short_rest_open (V38). The two
-- options are the DM's: move the campaign clock to the next morning on finish,
-- and (survival campaigns) whether the rest was undisturbed (-3 fatigue) or
-- disturbed (-1). All three clear on encounter start and session end.
ALTER TABLE combat_session
    ADD COLUMN IF NOT EXISTS long_rest_open          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS long_rest_advance_clock BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS long_rest_undisturbed   BOOLEAN NOT NULL DEFAULT TRUE;

-- Per-seat readiness. On the participant row so a player who joins mid-phase
-- starts not ready and one who leaves drops out of the count automatically.
ALTER TABLE session_participant
    ADD COLUMN IF NOT EXISTS long_rest_ready BOOLEAN NOT NULL DEFAULT FALSE;

-- The latest long rest outcome per session, broadcast on the poll snapshot so
-- every client can announce it once (same idea as session_loot_result, V48).
-- Replaced by the next outcome; purged when the session ends.
CREATE TABLE IF NOT EXISTS session_long_rest_result (
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT NOT NULL REFERENCES combat_session (id) ON DELETE CASCADE,
    outcome        TEXT NOT NULL,          -- FINISHED|INTERRUPTED
    skipped_names  TEXT,                   -- newline-separated heroes at 0 HP (FINISHED only)
    clock_advanced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_session_long_rest_result_session
    ON session_long_rest_result (session_id);
