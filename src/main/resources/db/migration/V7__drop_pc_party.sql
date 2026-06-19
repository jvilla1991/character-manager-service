-- The per-character `party` field was removed from the app: character grouping
-- is now driven solely by the campaign binding (pc.campaign_id). Drop the
-- now-unused column. Safe/idempotent for environments where it never existed.
ALTER TABLE pc
    DROP COLUMN IF EXISTS party;
