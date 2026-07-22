-- Every purchasable item must carry a Darker Dungeons bulk rating so the
-- inventory systems (purchase stamping, join-time conversion, slot math) use
-- BULK, never raw weight. V39 stamped the official p. 59-60 table values; the
-- handful of SRD items those tables don't list get ratings derived from the
-- p. 56 scale here, and the column then locks NOT NULL so no future seed can
-- add an unrated item.
UPDATE srd_item AS s SET bulk = v.bulk
FROM (VALUES
  -- Tiny 0.2 — smaller than the palm of your hand, negligible weight
  ('map',                  0.2),
  ('string',               0.2),
  ('spell-scroll-cantrip', 0.2),
  ('spell-scroll-level-1', 0.2),
  -- Small 1 — up to a handspan / up to 2 lb
  ('basket',               1.0),
  ('pouch',                1.0),
  ('quiver',               1.0),
  ('sack',                 1.0),
  -- Medium 2 — up to an arm's length / up to 5 lb
  ('backpack',             2.0),
  ('robe',                 2.0)
) AS v(item_key, bulk)
WHERE s.item_key = v.item_key;

-- Safety net for any straggler rows other environments may carry: derive the
-- rating from the p. 56 weight bands, exactly as BulkRules.bulkFromWeight
-- (unknown weight = Small 1, negligible = Tiny 0.2).
UPDATE srd_item SET bulk = CASE
    WHEN weight IS NULL THEN 1.0
    WHEN weight <= 0    THEN 0.2
    WHEN weight <= 2    THEN 1.0
    WHEN weight <= 5    THEN 2.0
    WHEN weight <= 10   THEN 3.0
    WHEN weight <= 35   THEN 6.0
    ELSE 9.0 END
  WHERE bulk IS NULL;

ALTER TABLE srd_item ALTER COLUMN bulk SET NOT NULL;
