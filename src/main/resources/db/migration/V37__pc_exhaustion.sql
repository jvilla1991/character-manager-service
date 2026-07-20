-- 2024 PHB Exhaustion level (0-6): each level is -2 to all d20 Tests and
-- -5 ft Speed; level 6 kills the character. NULL = never tracked (reads as 0).
-- Edited from the character sheet by the owner or the campaign's DM; an update
-- body carrying NULL preserves the stored value (same rule as survival — see
-- PCService.preserveServerOwnedColumns), so a payload built without the field
-- can never wipe a real level.
ALTER TABLE pc
    ADD COLUMN IF NOT EXISTS exhaustion SMALLINT;
