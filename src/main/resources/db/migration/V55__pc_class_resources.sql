-- Limited-use class features (Second Wind, Rage, Channel Divinity, Lay On
-- Hands, Focus Points, ...) tracked per character.
--
-- Only the amount USED is stored, as a JSON object keyed by resource:
--   {"secondWind":1,"actionSurge":1,"layOnHands":15}
-- The maximum is always computed by the application from the character's
-- class, subclass, level and ability scores, so it never goes stale here. A
-- resource with nothing used is not stored; NULL = nothing used at all.
ALTER TABLE pc
    ADD COLUMN IF NOT EXISTS class_resources TEXT;
