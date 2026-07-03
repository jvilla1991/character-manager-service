-- In-world campaign clock for the Darker Dungeons survival-conditions variant
-- (useful to any campaign). JSON-as-TEXT, house style like threads/variant_rules:
--   {"year": 1492, "month": 3, "day": 12, "timeOfDay": "dawn"}
-- timeOfDay cycles dawn -> noon -> dusk -> night; NULL = clock never set.
-- Written only by the session time endpoints (and optionally at creation) —
-- campaign updates pin it server-side so a stale client body can't null it.
ALTER TABLE campaign ADD COLUMN IF NOT EXISTS game_time TEXT;
