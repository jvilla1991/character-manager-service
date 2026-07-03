-- Darker Dungeons survival conditions (ch. 31): per-character hunger / thirst /
-- fatigue stages 0-6. JSON-as-TEXT like the other pc JSON columns:
--   {"hunger": 2, "thirst": 2, "fatigue": 2}
-- NULL = never tracked (treated as all zeros). Only meaningful in campaigns with
-- the survivalConditions variant rule; inert everywhere else.
ALTER TABLE pc ADD COLUMN IF NOT EXISTS survival TEXT;
