-- Post-combat loot. Two layers, mirroring the shop feature's curated/session split:
--   * encounter loot — persistent prep attached to a curated encounter (like
--     shop_item attaches to shop). Coins are a single copper pile on the
--     encounter header (integer cp, float-free — 1 gp = 100 cp).
--   * session_loot / session_loot_item — the transient pool a DM opens in a live
--     session, optionally seeded by COPYING an encounter's loot (the curated rows
--     are never mutated by a session). `dropped` is the visibility flip: false =
--     DM-only draft, true = players may claim. Claims write through to the
--     canonical pc row and only decrement *_remaining here; unclaimed remainder
--     is deleted when the session ends.

ALTER TABLE encounter ADD COLUMN loot_coin_cp BIGINT NOT NULL DEFAULT 0;

-- One line per loot item: either a catalog reference (srd_item.item_key) or a
-- free-hand custom item (magic items, trophies) — exactly one of the two.
-- Duplicate lines are allowed (no unique constraint), unlike shop_item.
CREATE TABLE IF NOT EXISTS encounter_loot_item (
    id                BIGSERIAL PRIMARY KEY,
    encounter_id      BIGINT NOT NULL REFERENCES encounter (id) ON DELETE CASCADE,
    catalog_item_key  TEXT,                      -- srd_item.item_key; NULL for custom items
    custom_name       TEXT,                      -- free-hand item name; NULL for catalog items
    custom_notes      TEXT,                      -- description for custom items
    qty               INT NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((catalog_item_key IS NULL) <> (custom_name IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_encounter_loot_item_encounter_id
    ON encounter_loot_item (encounter_id);

-- The one loot pool a DM has opened in a live session (unique index mirrors
-- ux_session_shop_one_per_session).
CREATE TABLE IF NOT EXISTS session_loot (
    id                 BIGSERIAL PRIMARY KEY,
    session_id         BIGINT NOT NULL REFERENCES combat_session (id) ON DELETE CASCADE,
    name               TEXT,                     -- label, e.g. the seeding encounter's name
    dropped            BOOLEAN NOT NULL DEFAULT FALSE,
    coin_cp_total      BIGINT NOT NULL DEFAULT 0,
    coin_cp_remaining  BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_session_loot_one_per_session
    ON session_loot (session_id);

-- qty_remaining is the only claim state (first-come-first-served); who took
-- what is recorded in the pc activity log, not here.
CREATE TABLE IF NOT EXISTS session_loot_item (
    id                BIGSERIAL PRIMARY KEY,
    session_loot_id   BIGINT NOT NULL REFERENCES session_loot (id) ON DELETE CASCADE,
    catalog_item_key  TEXT,
    custom_name       TEXT,
    custom_notes      TEXT,
    qty               INT NOT NULL DEFAULT 1,
    qty_remaining     INT NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((catalog_item_key IS NULL) <> (custom_name IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_session_loot_item_pool
    ON session_loot_item (session_loot_id);
