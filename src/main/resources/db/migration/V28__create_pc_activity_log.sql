-- Per-character activity log: a read-only audit trail of notable server-side
-- events (level-ups, shop purchases/sales, DM XP awards, long rests, DM
-- edits). Unlike pc_note (player-authored), every row here is written by a
-- backend mutation as a pre-rendered display string — there is no client
-- input. The table prunes to the latest 10 rows per character (see
-- PcActivityLogRepository#pruneToLatest); this is a recent-activity feed,
-- not a full history.
CREATE TABLE IF NOT EXISTS pc_activity_log (
    id            BIGSERIAL PRIMARY KEY,
    pc_id         BIGINT NOT NULL REFERENCES pc (id) ON DELETE CASCADE,
    action_type   VARCHAR(32) NOT NULL,
    description   TEXT NOT NULL,
    actor_user_id UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- id DESC tiebreaker matters: multiple entries inserted in one transaction
-- (e.g. a DM diff with several changes) can share the same created_at instant.
CREATE INDEX IF NOT EXISTS idx_pc_activity_log_pc_created
    ON pc_activity_log (pc_id, created_at DESC, id DESC);
