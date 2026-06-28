-- Shopping feature, Phase 1 (MVP).
--
-- Two storage styles, matched to contention (see the design):
--   * srd_item     — read-only, shared reference catalog (normalized table).
--   * session_shop / session_shop_attendee — transient, session-scoped state,
--     mirroring combat_session / session_participant. Attendance is NEVER stored
--     on the pc row; it dies with the shop or the session via ON DELETE CASCADE.

-- --- SRD reference catalog ------------------------------------------------
-- One row per standard item, discriminated by `category`. Category-specific
-- attributes live in the `details` JSON-TEXT column (house JSON-as-TEXT style),
-- so each catalog slice (weapons / armor / material components) can add its own
-- shape without per-slice schema churn. Prices are normalized to COPPER as an
-- integer to keep the coin math float-free (1 gp = 100 cp).
CREATE TABLE IF NOT EXISTS srd_item (
    id          BIGSERIAL PRIMARY KEY,
    item_key    TEXT NOT NULL UNIQUE,            -- stable slug, e.g. 'longsword'
    name        TEXT NOT NULL,
    category    TEXT NOT NULL,                   -- WEAPON | ARMOR | MATERIAL_COMPONENT
    cost_cp     BIGINT NOT NULL,                 -- price in copper pieces
    weight      NUMERIC,                         -- in pounds; null = negligible
    source      TEXT NOT NULL DEFAULT 'SRD-2024',
    details     TEXT                             -- category-specific JSON
);

CREATE INDEX IF NOT EXISTS idx_srd_item_category ON srd_item (category);

-- --- Transient session shop ----------------------------------------------
-- The one shop a DM has activated in a live session. At most one per session
-- (partial-unique discipline mirrors V8's one-session-per-campaign rule).
-- `shop_id` is a forward hook for Phase 2 DM-curated shops; NULL = a standard
-- shop derived from srd_item at catalog price with unlimited stock.
CREATE TABLE IF NOT EXISTS session_shop (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES combat_session (id) ON DELETE CASCADE,
    category      TEXT NOT NULL,                 -- which catalog slice this shop sells
    settlement    TEXT,                          -- free-text label typed by the DM
    shop_id       BIGINT,                        -- NULL now; curated-shop FK later
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_session_shop_one_per_session
    ON session_shop (session_id);

-- --- Shop attendance (DM-targeted characters) ----------------------------
-- The characters the DM has placed at the shop. Visible-to and purchasable-by
-- only these PCs (plus the DM). Cleared when the shop closes or the session
-- ends, via the cascade chain combat_session -> session_shop -> attendee.
CREATE TABLE IF NOT EXISTS session_shop_attendee (
    id               BIGSERIAL PRIMARY KEY,
    session_shop_id  BIGINT NOT NULL REFERENCES session_shop (id) ON DELETE CASCADE,
    pc_id            BIGINT NOT NULL REFERENCES pc (id) ON DELETE CASCADE,
    owner_user_id    UUID NOT NULL,             -- denormalized for visibility checks
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_attendee_shop_pc
    ON session_shop_attendee (session_shop_id, pc_id);

CREATE INDEX IF NOT EXISTS idx_attendee_shop ON session_shop_attendee (session_shop_id);
