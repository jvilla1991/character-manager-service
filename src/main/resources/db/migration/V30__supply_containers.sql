-- Supply containers (Darker Dungeons): ration boxes and waterskins are
-- CONTAINERS, each holding 5 servings; capacity = containers × 5. The servings
-- themselves are tiny — rations reprice to 1 sp each at 0.2 lb / 0.2 bulk; the
-- waterskin keeps its 2 sp price but weighs as the 1-bulk container it is.
-- Water charges are refilled free at any source, so they have no catalog row
-- and are never sold. Idempotent on item_key.
UPDATE srd_item SET cost_cp = 10, weight = 0.2, bulk = 0.2 WHERE item_key = 'rations';
UPDATE srd_item SET cost_cp = 20, weight = 1, bulk = 1.0 WHERE item_key = 'waterskin';
INSERT INTO srd_item (item_key, name, category, cost_cp, weight, bulk, source, details) VALUES
  ('ration-box', 'Ration box', 'GEAR', 50, 1, 1.0, 'DD', '{}')
ON CONFLICT (item_key) DO NOTHING;
