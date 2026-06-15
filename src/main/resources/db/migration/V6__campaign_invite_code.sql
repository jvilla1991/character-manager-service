-- Invite code lets a player bind their own character to a DM's campaign by
-- consent (they enter the code). Unique; existing rows keep NULL until reissued.
ALTER TABLE campaign
    ADD COLUMN IF NOT EXISTS invite_code TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_campaign_invite_code
    ON campaign (invite_code)
    WHERE invite_code IS NOT NULL;
