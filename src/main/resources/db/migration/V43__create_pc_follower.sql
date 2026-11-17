-- Followers: companions (a hireling, a familiar, a rescued squire) the campaign's
-- DM grants to a character. Each one carries its own stat block, which the
-- client renders as a cut-down character sheet (Sheet / Spells / Inventory).
--
-- stat_block is an opaque JSON object in the client's own sheet shape — the
-- server never interprets it, it only stores and returns it (the same contract
-- as the JSON TEXT columns on pc). Kept in its own table rather than as another
-- column on pc so that a generic full-entity PC save can never clobber it.
--
-- Only the DM grants and dismisses; the owning player (and the DM) may update
-- the stat block as the follower takes damage, spends slots or gains gear.
CREATE TABLE IF NOT EXISTS pc_follower (
    id                 BIGSERIAL PRIMARY KEY,
    pc_id              BIGINT NOT NULL REFERENCES pc (id) ON DELETE CASCADE,
    name               VARCHAR(100) NOT NULL,
    stat_block         TEXT NOT NULL,
    granted_by_user_id UUID NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pc_follower_pc_id ON pc_follower (pc_id);
