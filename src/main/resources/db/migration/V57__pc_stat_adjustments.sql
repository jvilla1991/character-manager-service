-- Labelled lines a Dungeon Master adds to a calculated number on the sheet, e.g.
-- "+1 | Taunka" under Armor Class. One JSON object keyed by the number adjusted:
--   {"ac":[{"value":1,"label":"Taunka"}]}
-- TEXT like the other JSON columns on pc (languages, features, ...). NULL = the
-- character has none. The number's own column (pc.ac) keeps holding the finished
-- total, lines included, so nothing that reads it changes. The shape is checked
-- in the service layer (StatAdjustmentRules) — no DB CHECK here.
ALTER TABLE pc ADD COLUMN stat_adjustments TEXT;
