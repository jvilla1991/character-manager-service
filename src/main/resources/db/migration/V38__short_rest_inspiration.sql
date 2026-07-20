-- Short rests + Heroic Inspiration (2024 PHB).
--
-- pc.hit_dice_used: how many of the character's hit dice are spent (max = level).
-- Spent during a DM-opened short-rest window; a long rest restores max(1, level/2).
-- Server-owned: only the session endpoints mutate it, generic sheet PUTs never do
-- (PCService.preserveServerOwnedColumns).
--
-- pc.inspiration_pips (0-4) + pc.heroic_inspiration: the DM awards pips one at a
-- time; the fifth pip empties the meter and grants Heroic Inspiration. Both are
-- server-owned like hit_dice_used.
--
-- combat_session.short_rest_open: the DM-announced short-rest window during which
-- players may spend hit dice. Cleared on encounter start and session end.
ALTER TABLE pc
    ADD COLUMN IF NOT EXISTS hit_dice_used SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS inspiration_pips SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS heroic_inspiration BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE combat_session
    ADD COLUMN IF NOT EXISTS short_rest_open BOOLEAN NOT NULL DEFAULT FALSE;
