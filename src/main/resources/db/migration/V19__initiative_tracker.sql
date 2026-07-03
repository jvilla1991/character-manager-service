-- Initiative Tracker: enemies, visibility, and the encounter turn sound.
--
-- dex_modifier: the DM enters an enemy's DEX modifier by hand (they calculate
-- it); it is the initiative tie-breaker for NPC rows. PC rows leave it NULL —
-- their modifier is derived from the canonical pc.ability_dex at sort time, so
-- there is no copy to drift.
ALTER TABLE session_participant
    ADD COLUMN IF NOT EXISTS dex_modifier SMALLINT;

-- enemies_hidden: the DM checkbox. TRUE (default) means players' snapshots omit
-- enemy combatants entirely — no masked slots. turn_sound: encounter-level cue
-- key chosen by the DM, played client-side on turn change; NULL = silent.
ALTER TABLE combat_session
    ADD COLUMN IF NOT EXISTS enemies_hidden BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE combat_session
    ADD COLUMN IF NOT EXISTS turn_sound TEXT;
