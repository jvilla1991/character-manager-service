-- Bind a character to a campaign. Nullable: a PC may belong to no campaign.
-- ON DELETE SET NULL: deleting a campaign unbinds its members rather than
-- cascading away the characters. `party` is kept for display / back-compat.
ALTER TABLE pc
    ADD COLUMN IF NOT EXISTS campaign_id BIGINT;

ALTER TABLE pc
    ADD CONSTRAINT fk_pc_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_pc_campaign_id ON pc (campaign_id);
