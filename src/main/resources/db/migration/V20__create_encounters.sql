-- DM-curated encounters. A persistent, reusable encounter a DM defines once
-- (a free-hand list of enemy creatures) and later loads into a live session,
-- where each creature becomes an enemy combatant in the initiative tracker.
--
-- Unlike curated shops, creatures are stored inline (no SRD catalog reference):
-- name, DM-calculated DEX modifier, optional max HP, and a quantity that expands
-- into numbered rows on load (e.g. Goblin 1..Goblin 4). Loading only creates
-- session_participant rows — there is no transient per-session encounter table.
CREATE TABLE IF NOT EXISTS encounter (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    dm_user_id   UUID NOT NULL,                 -- the campaign's DM (owner); authz checks this
    name         TEXT NOT NULL,
    notes        TEXT,                          -- free-text DM notes (tactics, terrain, read-aloud)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_encounter_campaign_id ON encounter (campaign_id);

-- One line per creature in a curated encounter. Data is stored inline (no catalog
-- ref), so duplicate names are allowed (no unique constraint). quantity expands
-- into that many numbered enemy combatants when the encounter is loaded.
CREATE TABLE IF NOT EXISTS encounter_creature (
    id            BIGSERIAL PRIMARY KEY,
    encounter_id  BIGINT NOT NULL REFERENCES encounter (id) ON DELETE CASCADE,
    name          TEXT NOT NULL,
    dex_modifier  SMALLINT NOT NULL,            -- DM-calculated initiative tie-breaker
    hp_max        SMALLINT,                     -- NULL = untracked HP
    quantity      INT NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_encounter_creature_encounter_id
    ON encounter_creature (encounter_id);
