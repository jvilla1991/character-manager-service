-- DM Session Notes: a campaign-scoped running log a Dungeon Master appends to,
-- either from the campaign menu (out of session) or live during a session.
--
-- Notes belong to a campaign. session_id optionally records which live session a
-- note was taken in; ON DELETE SET NULL keeps the note as part of the campaign's
-- history even after that session row is gone. Only the owning DM can read or
-- write notes (enforced in the service, mirroring campaign ownership).
CREATE TABLE IF NOT EXISTS session_note (
    id          BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    session_id  BIGINT REFERENCES combat_session (id) ON DELETE SET NULL,  -- set when taken in-session
    dm_user_id  UUID NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_session_note_campaign_id ON session_note (campaign_id);
