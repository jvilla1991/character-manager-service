-- Campaigns a Dungeon Master runs. Owned by dm_user_id (the auth-service user
-- UUID). Members are PCs bound via pc.campaign_id (added in V5).
CREATE TABLE IF NOT EXISTS campaign (
    id            BIGSERIAL PRIMARY KEY,
    dm_user_id    UUID NOT NULL,
    name          TEXT NOT NULL,
    party         TEXT,
    setting       TEXT,
    session       SMALLINT,
    next_session  TEXT,
    arc           TEXT,
    tint          TEXT,
    chronicle     TEXT,
    secrets       TEXT,
    threads       TEXT,                 -- JSON array of open plot threads
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_campaign_dm_user_id ON campaign (dm_user_id);
