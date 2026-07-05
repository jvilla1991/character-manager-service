-- Per-character session notes: a running log the OWNING PLAYER writes on their
-- character sheet ("what my character remembers"). The campaign DM may read a
-- member's notes via the existing cross-link; only the owner writes.
--
-- session_id optionally records which live session a note was taken in
-- (ON DELETE SET NULL keeps it as history after the session row is gone);
-- campaign_id snapshots the campaign at write time for the same reason.
CREATE TABLE IF NOT EXISTS pc_note (
    id             BIGSERIAL PRIMARY KEY,
    pc_id          BIGINT NOT NULL REFERENCES pc (id) ON DELETE CASCADE,
    campaign_id    BIGINT REFERENCES campaign (id) ON DELETE SET NULL,
    session_id     BIGINT REFERENCES combat_session (id) ON DELETE SET NULL,
    author_user_id UUID NOT NULL,
    body           TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pc_note_pc_id ON pc_note (pc_id);
