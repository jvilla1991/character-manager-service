-- Party loot split. Dropped loot is no longer first-come-first-served: the
-- seated players divide the pool between their characters, then vote on the
-- split. Majority ✓ distributes it; any ✗ voter who also chooses PvP cancels
-- it (the pool goes back to the DM as an undropped draft).
--
-- split_phase: NULL while the pool is a DM draft; SPLITTING once dropped
-- (players move items/coins between the pile and characters); VOTING once a
-- player locks the split for a vote.
ALTER TABLE session_loot ADD COLUMN IF NOT EXISTS split_phase TEXT;

-- Pools dropped under the old claim model: what was already claimed has left
-- the pool, so the remainder becomes the whole pool to split. Fully claimed
-- lines are dropped. qty_remaining / coin_cp_remaining are no longer claim
-- state after this migration (the API reports the unallocated pile instead).
UPDATE session_loot
   SET split_phase = 'SPLITTING',
       coin_cp_total = coin_cp_remaining
 WHERE dropped = TRUE;
DELETE FROM session_loot_item i
 USING session_loot l
 WHERE i.session_loot_id = l.id AND l.dropped = TRUE AND i.qty_remaining = 0;
UPDATE session_loot_item i
   SET qty = i.qty_remaining
  FROM session_loot l
 WHERE i.session_loot_id = l.id AND l.dropped = TRUE;

-- Who gets what in the proposed split. An item row (loot_item_id set) gives a
-- character qty of that line; a coin row (loot_item_id NULL) gives them coin_cp.
-- Both cascade away with the pool, the line, or a participant who leaves —
-- whatever they held returns to the pile.
CREATE TABLE IF NOT EXISTS session_loot_allocation (
    id               BIGSERIAL PRIMARY KEY,
    session_loot_id  BIGINT NOT NULL REFERENCES session_loot (id) ON DELETE CASCADE,
    participant_id   BIGINT NOT NULL REFERENCES session_participant (id) ON DELETE CASCADE,
    loot_item_id     BIGINT REFERENCES session_loot_item (id) ON DELETE CASCADE,
    qty              INT NOT NULL DEFAULT 0,
    coin_cp          BIGINT NOT NULL DEFAULT 0,
    CHECK ((loot_item_id IS NOT NULL AND qty > 0 AND coin_cp = 0)
        OR (loot_item_id IS NULL AND qty = 0 AND coin_cp > 0))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_loot_allocation_item
    ON session_loot_allocation (session_loot_id, participant_id, loot_item_id)
    WHERE loot_item_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_loot_allocation_coins
    ON session_loot_allocation (session_loot_id, participant_id)
    WHERE loot_item_id IS NULL;

-- One ballot per seated PC, snapshotted when the split is locked for a vote.
-- vote NULL = not yet voted; TRUE = ✓; FALSE = ✗. pvp is only meaningful on a
-- ✗: NULL = still deciding, TRUE = PvP (cancels the loot), FALSE = just object.
CREATE TABLE IF NOT EXISTS session_loot_ballot (
    id               BIGSERIAL PRIMARY KEY,
    session_loot_id  BIGINT NOT NULL REFERENCES session_loot (id) ON DELETE CASCADE,
    participant_id   BIGINT NOT NULL REFERENCES session_participant (id) ON DELETE CASCADE,
    display_name     TEXT NOT NULL,
    vote             BOOLEAN,
    pvp              BOOLEAN,
    UNIQUE (session_loot_id, participant_id)
);

-- The latest split outcome per session, broadcast on the poll snapshot so
-- every client can announce it once (the pool itself is gone or hidden by
-- then). Replaced by the next outcome; purged when the session ends.
CREATE TABLE IF NOT EXISTS session_loot_result (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES combat_session (id) ON DELETE CASCADE,
    loot_name     TEXT,
    outcome       TEXT NOT NULL, -- APPROVED|PVP|NO_MAJORITY|DM_APPROVED|DM_CANCELLED
    pvp_callers   TEXT,          -- comma-separated display names (PVP only)
    agree_count   INT NOT NULL DEFAULT 0,
    object_count  INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_session_loot_result_session
    ON session_loot_result (session_id);
