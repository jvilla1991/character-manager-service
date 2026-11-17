-- Factions belong to a campaign; each character has its own independent scores.
-- Missing score rows mean zero, including for characters who join later.
CREATE TABLE campaign_faction (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaign(id) ON DELETE CASCADE,
    parent_id BIGINT,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    UNIQUE (id, campaign_id),
    FOREIGN KEY (parent_id, campaign_id) REFERENCES campaign_faction(id, campaign_id) ON DELETE CASCADE,
    CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX idx_campaign_faction_campaign ON campaign_faction(campaign_id);

CREATE TABLE pc_renown (
    id BIGSERIAL PRIMARY KEY,
    pc_id BIGINT NOT NULL REFERENCES pc(id) ON DELETE CASCADE,
    faction_id BIGINT NOT NULL REFERENCES campaign_faction(id) ON DELETE CASCADE,
    score INTEGER NOT NULL DEFAULT 0 CHECK (score BETWEEN -100 AND 100),
    UNIQUE (pc_id, faction_id)
);
