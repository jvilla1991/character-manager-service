-- Phase 2: DM-curated shops. A persistent, reusable shop a DM defines once
-- (a chosen subset of the SRD catalog with optional price overrides) and later
-- activates into a live session via session_shop.shop_id.
--
-- Phase 2 MVP: catalog items only, with price overrides; quantity is unlimited
-- (no stock column yet). Finite per-item stock + concurrency-safe decrement is a
-- deferred follow-up and will add a quantity column then.
CREATE TABLE IF NOT EXISTS shop (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    dm_user_id   UUID NOT NULL,                 -- the campaign's DM (owner); authz checks this
    name         TEXT NOT NULL,
    settlement   TEXT,                          -- default settlement label when activated
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_shop_campaign_id ON shop (campaign_id);

-- One line per catalog item in a curated shop. price_cp NULL = use the catalog
-- price; a value overrides it. catalog_item_key references the reference catalog.
CREATE TABLE IF NOT EXISTS shop_item (
    id                BIGSERIAL PRIMARY KEY,
    shop_id           BIGINT NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    catalog_item_key  TEXT NOT NULL REFERENCES srd_item (item_key),
    price_cp          BIGINT,                    -- NULL = inherit catalog price
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A catalog item appears at most once per shop.
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_item_shop_catalog
    ON shop_item (shop_id, catalog_item_key);

CREATE INDEX IF NOT EXISTS idx_shop_item_shop_id ON shop_item (shop_id);
