-- DM-granted level-up permission. A player may level up when their XP crosses
-- the next 2024 PHB threshold OR when the campaign's DM grants a pending
-- level-up; applying the level-up clears the flag. Server-owned: generic PC
-- update bodies never change it (see PCService.preserveServerOwnedColumns).
ALTER TABLE pc
    ADD COLUMN IF NOT EXISTS pending_level_grant BOOLEAN NOT NULL DEFAULT FALSE;
