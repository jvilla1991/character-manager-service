-- Per-session combat log (the DM's "Combat Log" panel in Session Mode): one row
-- for each health or condition change made while a session is live. Rows exist
-- only while the session lives — deleted explicitly when it ends
-- (SessionService#endSession / the lazy TTL-expiry path), like session_roll, so
-- this is never a long-term history table. ON DELETE CASCADE is a backstop for
-- the (rare) case a combat_session row itself is hard-deleted via the campaign
-- cascade chain.
CREATE TABLE IF NOT EXISTS session_combat_log (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES combat_session (id) ON DELETE CASCADE,
    event_type    VARCHAR(20) NOT NULL,
    subject_name  TEXT NOT NULL,
    description   TEXT NOT NULL,
    round         SMALLINT,
    actor_user_id UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The panel reads newest-first, one page at a time.
CREATE INDEX IF NOT EXISTS idx_session_combat_log_session
    ON session_combat_log (session_id, id DESC);
