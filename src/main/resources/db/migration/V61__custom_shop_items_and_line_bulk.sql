-- Custom (non-catalog) items in curated shops, and an explicit bulk rating on
-- every custom line.
--
-- 1. shop_item: a line is now EITHER a catalog reference (catalog_item_key) OR
--    a custom item the DM wrote out (custom_name + its own stats) — the same
--    key-or-name shape curated_loot_item has had since V35. A custom line's
--    price_cp is the price itself (there is no catalog price to fall back on).
--    The (shop_id, catalog_item_key) unique index still stands: NULL keys are
--    distinct in Postgres, so a shop may hold any number of custom lines.
--
-- 2. bulk on custom lines (shop, curated loot, session loot pool): the Darker
--    Dungeons bulk a DM wants for the item. NULL keeps today's behaviour — the
--    bulk is worked out from the weight when the item reaches an inventory.

ALTER TABLE shop_item ALTER COLUMN catalog_item_key DROP NOT NULL;

ALTER TABLE shop_item
    ADD COLUMN IF NOT EXISTS custom_name  TEXT,     -- free-hand item name; NULL for catalog items
    ADD COLUMN IF NOT EXISTS custom_notes TEXT,     -- description (and any "Requires: ..." line)
    ADD COLUMN IF NOT EXISTS category     TEXT,     -- custom lines: weapon|armor|material-component|gear|transport|treasure
    ADD COLUMN IF NOT EXISTS weight       NUMERIC,  -- custom lines: pounds
    ADD COLUMN IF NOT EXISTS bulk         NUMERIC,  -- custom lines: explicit bulk; NULL = derive from weight
    ADD COLUMN IF NOT EXISTS damage       TEXT,     -- custom weapon lines, e.g. '1d8 slashing'
    ADD COLUMN IF NOT EXISTS armor_class  TEXT;     -- custom armor lines, e.g. '14 + Dex modifier (max 2)'

-- Exactly one of the two: every existing row has a key and no name, so it passes.
ALTER TABLE shop_item
    ADD CONSTRAINT chk_shop_item_key_or_name
    CHECK ((catalog_item_key IS NULL) <> (custom_name IS NULL));

ALTER TABLE curated_loot_item ADD COLUMN IF NOT EXISTS bulk NUMERIC;
ALTER TABLE session_loot_item ADD COLUMN IF NOT EXISTS bulk NUMERIC;
