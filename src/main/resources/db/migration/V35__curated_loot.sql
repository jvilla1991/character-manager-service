-- Curated loot becomes a first-class, campaign-scoped entity (like curated shops
-- and curated encounters), fully decoupled from encounters. A DM preps reusable
-- loot lists on the campaign screen and drops one into a live session's claim
-- pool (copy semantics — a list survives being dropped repeatedly).
--
-- This migration also:
--   * enriches custom loot lines with the same attributes a DM grant carries
--     (category / unit_cost_cp / weight / damage / armor_class), on both the
--     curated lines and the session pool copies, so claimed custom items no
--     longer arrive stat-less;
--   * converts every encounter's prepped loot into a standalone curated loot
--     list (data-preserving), then removes loot from encounters entirely;
--   * replaces the encounter creature's dex_modifier with armor_class (AC is
--     what a DM wants at a glance; initiative ties now fall back to insertion
--     order) — same swap on session_participant enemy rows;
--   * adds updated_at to session_note (DM session notes become editable).

-- ── 1. Curated loot lists ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS curated_loot (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    dm_user_id   UUID NOT NULL,                 -- the campaign's DM (owner); authz checks this
    name         TEXT NOT NULL,
    notes        TEXT,
    coin_cp      BIGINT NOT NULL DEFAULT 0,     -- prepped coin pile (integer cp, 1 gp = 100 cp)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_curated_loot_campaign_id ON curated_loot (campaign_id);

-- One line per loot item: either a catalog reference (srd_item.item_key) or a
-- free-hand custom item — exactly one of the two. The attribute columns apply
-- to custom lines only (catalog lines take their stats from the catalog):
-- category is the lowercase inventory label (weapon/armor/material-component/gear).
CREATE TABLE IF NOT EXISTS curated_loot_item (
    id                BIGSERIAL PRIMARY KEY,
    loot_id           BIGINT NOT NULL REFERENCES curated_loot (id) ON DELETE CASCADE,
    catalog_item_key  TEXT,                     -- srd_item.item_key; NULL for custom items
    custom_name       TEXT,                     -- free-hand item name; NULL for catalog items
    custom_notes      TEXT,
    category          TEXT,                     -- custom lines: weapon|armor|material-component|gear
    unit_cost_cp      BIGINT,                   -- custom lines: value in copper
    weight            NUMERIC,                  -- custom lines: pounds
    damage            TEXT,                     -- custom weapon lines, e.g. '1d8 slashing'
    armor_class       TEXT,                     -- custom armor lines, e.g. '14 + Dex modifier (max 2)'
    qty               INT NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((catalog_item_key IS NULL) <> (custom_name IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_curated_loot_item_loot_id ON curated_loot_item (loot_id);

-- ── 2. Convert encounter-attached loot into standalone lists (no data loss) ──

ALTER TABLE curated_loot ADD COLUMN legacy_encounter_id BIGINT;

INSERT INTO curated_loot (campaign_id, dm_user_id, name, coin_cp, legacy_encounter_id)
SELECT e.campaign_id, e.dm_user_id, e.name || ' loot', e.loot_coin_cp, e.id
FROM encounter e
WHERE e.loot_coin_cp > 0
   OR EXISTS (SELECT 1 FROM encounter_loot_item i WHERE i.encounter_id = e.id);

INSERT INTO curated_loot_item (loot_id, catalog_item_key, custom_name, custom_notes, qty, created_at)
SELECT cl.id, i.catalog_item_key, i.custom_name, i.custom_notes, i.qty, i.created_at
FROM encounter_loot_item i
JOIN curated_loot cl ON cl.legacy_encounter_id = i.encounter_id;

ALTER TABLE curated_loot DROP COLUMN legacy_encounter_id;

DROP TABLE encounter_loot_item;
ALTER TABLE encounter DROP COLUMN loot_coin_cp;

-- ── 3. Session pool copies carry the same custom-item attributes ────────────

ALTER TABLE session_loot_item ADD COLUMN category     TEXT;
ALTER TABLE session_loot_item ADD COLUMN unit_cost_cp BIGINT;
ALTER TABLE session_loot_item ADD COLUMN weight       NUMERIC;
ALTER TABLE session_loot_item ADD COLUMN damage       TEXT;
ALTER TABLE session_loot_item ADD COLUMN armor_class  TEXT;

-- ── 4. Encounter creatures: dex_modifier → armor_class ──────────────────────
-- AC is display/reference info; existing creatures get NULL (unknown), never a
-- value derived from DEX. Initiative ties fall back to id (insertion) order.

ALTER TABLE encounter_creature ADD COLUMN armor_class SMALLINT;
ALTER TABLE encounter_creature DROP COLUMN dex_modifier;

-- Enemy combatant rows in live sessions: same swap (npc_armor_class surfaces as
-- the enemy's AC in the initiative tracker; PC rows keep AC on the pc record).
ALTER TABLE session_participant ADD COLUMN npc_armor_class SMALLINT;
ALTER TABLE session_participant DROP COLUMN dex_modifier;

-- ── 5. DM session notes become editable ─────────────────────────────────────
-- NULL = never edited; set on each edit and surfaced in the DTO.

ALTER TABLE session_note ADD COLUMN updated_at TIMESTAMPTZ;
