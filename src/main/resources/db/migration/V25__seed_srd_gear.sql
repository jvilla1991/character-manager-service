-- Adventuring-gear consumables for the survival-conditions variant: eating a
-- ration relieves 1 hunger, drinking from a waterskin relieves 1 thirst (a full
-- waterskin is treated as a stack of water rations). SRD 2024 prices/weights;
-- bulk 1 each (Darker Dungeons p. 59 "Small" band). Idempotent on item_key.
INSERT INTO srd_item (item_key, name, category, cost_cp, weight, bulk, source, details) VALUES
  ('rations',   'Rations (1 day)', 'GEAR', 50, 2, 1.0, 'SRD-2024', '{}'),
  ('waterskin', 'Waterskin',       'GEAR', 20, 5, 1.0, 'SRD-2024', '{}')
ON CONFLICT (item_key) DO NOTHING;
