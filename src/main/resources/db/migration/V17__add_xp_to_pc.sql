-- XP (experience points) for characters. Awarded by the DM in Session Mode and
-- accumulates only — level-up stays the explicit, player-driven flow. Existing
-- characters start at 0. Mirrors the scalar columns added in V3.
ALTER TABLE pc
    ADD COLUMN IF NOT EXISTS xp INTEGER NOT NULL DEFAULT 0;
