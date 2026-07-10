-- Ephemeral per-session dice-roll log (Session Mode "Roll Log" panel). Rows
-- exist only while the session lives: deleted explicitly when the session ends
-- (SessionService#endSession / the lazy TTL-expiry path), so this is never a
-- long-term history table. ON DELETE CASCADE is a backstop for the (rare) case
-- a combat_session row itself is hard-deleted via the campaign cascade chain.
CREATE TABLE IF NOT EXISTS session_roll (
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT NOT NULL REFERENCES combat_session (id) ON DELETE CASCADE,
    participant_id BIGINT REFERENCES session_participant (id) ON DELETE SET NULL,
    owner_user_id  UUID NOT NULL,
    display_name   TEXT NOT NULL,
    breakdown      TEXT NOT NULL,
    grand_total    INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_session_roll_session_created
    ON session_roll (session_id, created_at DESC, id DESC);
