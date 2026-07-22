-- Darker Dungeons ch. 10 catalog pass (PDF p. 56/59/60), three concerns:
--
--   1. Stamp the OFFICIAL per-item bulk ratings from the p. 60 equipment
--      tables onto the V36 gear rows. V36 left bulk NULL so BulkRules derived
--      it from weight bands — close, but wrong for the many "Tiny" items the
--      book rates 0.2 despite a non-zero weight (Acid at 1 lb banded to 1),
--      and for shape-based ratings (an Iron Pot is 10 lb but only 1 bulk).
--   2. Seed the p. 60 items the catalog was missing entirely (abacus,
--      whetstone, signet ring, ...). Cost/weight from SRD 5.1 (the 2024 SRD
--      dropped them), bulk from the DD table.
--   3. Seed the p. 59 Transportation table as a new TRANSPORT category so a
--      DM can grant mounts and vehicles. `details.notes` carries the ride
--      stats as display text; the DD "Slots" column (what the animal/vehicle
--      itself can carry) is reference info in the notes, not mechanics.
--
-- Bulk was NUMERIC(4,1) (max 999.9); ships go up to 4,860 — widen first.
-- Idempotent: UPDATEs re-set the same values, INSERTs no-op on item_key.
ALTER TABLE srd_item ALTER COLUMN bulk TYPE NUMERIC(6,1);

-- --- 1. Official DD p. 60 bulk for existing gear rows -----------------------
-- Deliberately untouched: rations / waterskin / ration-box (the V25/V30 supply
-- container model already stamps official values), and items DD doesn't list
-- (backpack, basket, pouch, quiver, sack, robe, map, string, spell scrolls) —
-- those keep the weight-band fallback.
UPDATE srd_item AS s SET bulk = v.bulk
FROM (VALUES
  -- Tiny (0.2): smaller than the palm of your hand, negligible weight
  ('acid',                 0.2),
  ('antitoxin',            0.2),
  ('basic-poison',         0.2),
  ('candle',               0.2),
  ('chalk',                0.2),
  ('ink',                  0.2),
  ('ink-pen',              0.2),
  ('paper',                0.2),
  ('parchment',            0.2),
  ('perfume',              0.2),
  ('piton',                0.2),
  ('signal-whistle',       0.2),
  ('soap',                 0.2),
  ('vial',                 0.2),
  -- Small (1)
  ('alchemists-fire',      1.0),
  ('arrows-20',            1.0),
  ('bolts-20',             1.0),
  ('sling-bullets-20',     1.0),
  ('needles-50',           1.0),
  ('ball-bearings',        1.0),
  ('bell',                 1.0),
  ('blanket',              1.0),
  ('block-and-tackle',     1.0),
  ('book',                 1.0),
  ('glass-bottle',         1.0),
  ('caltrops',             1.0),
  ('map-or-scroll-case',   1.0),
  ('crossbow-bolt-case',   1.0),
  ('chain',                1.0),
  ('climbers-kit',         1.0),
  ('travelers-clothes',    1.0),
  ('component-pouch',      1.0),
  ('druidic-focus-mistletoe', 1.0),
  ('druidic-focus-yew-wand',  1.0),
  ('flask',                1.0),
  ('gaming-set-playing-cards',     1.0),
  ('gaming-set-dice',              1.0),
  ('gaming-set-dragonchess',       1.0),
  ('gaming-set-three-dragon-ante', 1.0),
  ('grappling-hook',       1.0),
  ('hammer',               1.0),
  ('holy-symbol-amulet',   1.0),
  ('holy-symbol-emblem',   1.0),
  ('holy-symbol-reliquary',1.0),
  ('holy-water',           1.0),
  ('hunting-trap',         1.0),
  ('flute',                1.0),
  ('pan-flute',            1.0),
  ('jug',                  1.0),
  ('disguise-kit',         1.0),
  ('forgery-kit',          1.0),
  ('healers-kit',          1.0),
  ('herbalism-kit',        1.0),
  ('poisoners-kit',        1.0),
  ('lamp',                 1.0),
  ('bullseye-lantern',     1.0),
  ('hooded-lantern',       1.0),
  ('lock',                 1.0),
  ('magnifying-glass',     1.0),
  ('manacles',             1.0),
  ('mirror',               1.0),
  ('oil',                  1.0),
  ('iron-pot',             1.0),
  ('potion-of-healing',    1.0),
  ('spellbook',            1.0),
  ('iron-spikes',          1.0),
  ('spyglass',             1.0),
  ('tinderbox',            1.0),
  ('calligraphers-supplies', 1.0),
  ('carpenters-tools',     1.0),
  ('cartographers-tools',  1.0),
  ('cobblers-tools',       1.0),
  ('jewelers-tools',       1.0),
  ('navigators-tools',     1.0),
  ('thieves-tools',        1.0),
  ('tinkers-tools',        1.0),
  ('woodcarvers-tools',    1.0),
  ('torch',                1.0),
  ('arcane-focus-crystal', 1.0),
  ('arcane-focus-orb',     1.0),
  ('arcane-focus-rod',     1.0),
  ('arcane-focus-wand',    1.0),
  -- Medium (2)
  ('bedroll',              2.0),
  ('bucket',               2.0),
  ('costume',              2.0),
  ('fine-clothes',         2.0),
  ('crowbar',              2.0),
  ('drum',                 2.0),
  ('horn',                 2.0),
  ('lute',                 2.0),
  ('lyre',                 2.0),
  ('shawm',                2.0),
  ('viol',                 2.0),
  ('rope',                 2.0),
  ('alchemists-supplies',  2.0),
  ('brewers-supplies',     2.0),
  ('cooks-utensils',       2.0),
  ('glassblowers-tools',   2.0),
  ('leatherworkers-tools', 2.0),
  ('masons-tools',         2.0),
  ('painters-supplies',    2.0),
  ('potters-tools',        2.0),
  ('smiths-tools',         2.0),
  ('weavers-tools',        2.0),
  -- Large (3)
  ('arcane-focus-staff',   3.0),
  ('druidic-focus-wooden-staff', 3.0),
  ('bagpipes',             3.0),
  ('dulcimer',             3.0),
  ('ladder',               3.0),
  ('miners-pick',          3.0),
  ('pole',                 3.0),
  ('shovel',               3.0),
  ('sledgehammer',         3.0),
  ('tent',                 3.0),
  -- X-Large (6) and XX-Large (9)
  ('chest',                6.0),
  ('portable-ram',         6.0),
  ('barrel',               9.0),
  -- Equipment packs: DD lists official pack totals (p. 60)
  ('burglars-pack',       17.0),
  ('diplomats-pack',       9.0),
  ('dungeoneers-pack',    22.0),
  ('entertainers-pack',   11.0),
  ('explorers-pack',      20.0),
  ('priests-pack',        11.0),
  ('scholars-pack',        5.0)
) AS v(item_key, bulk)
WHERE s.item_key = v.item_key;

-- --- 2. p. 60 items missing from the catalog --------------------------------
-- Cost/weight from SRD 5.1 ('SRD-2014' provenance, as in V36); bulk official.
INSERT INTO srd_item (item_key, name, category, cost_cp, weight, bulk, source, details) VALUES
  ('abacus',              'Abacus',                       'GEAR',  200, 2,   1.0, 'SRD-2014', '{}'),
  ('common-clothes',      'Common Clothes',               'GEAR',   50, 3,   1.0, 'SRD-2014', '{}'),
  ('druidic-focus-totem', 'Druidic Focus (Totem)',        'GEAR',  100, 0,   1.0, 'SRD-2014', '{}'),
  ('fishing-tackle',      'Fishing Tackle',               'GEAR',  100, 4,   1.0, 'SRD-2014', '{}'),
  ('hourglass',           'Hourglass',                    'GEAR', 2500, 1,   1.0, 'SRD-2014', '{}'),
  ('merchants-scale',     'Merchant''s Scale',            'GEAR',  500, 3,   1.0, 'SRD-2014', '{}'),
  ('mess-kit',            'Mess Kit',                     'GEAR',   20, 1,   1.0, 'SRD-2014', '{}'),
  ('sealing-wax',         'Sealing Wax',                  'GEAR',   50, 0,   0.2, 'SRD-2014', '{}'),
  ('signet-ring',         'Signet Ring',                  'GEAR',  500, 0,   0.2, 'SRD-2014', '{}'),
  ('silk-rope',           'Rope, Silk (50 feet)',         'GEAR', 1000, 5,   1.0, 'SRD-2014', '{}'),
  ('whetstone',           'Whetstone',                    'GEAR',    1, 1,   1.0, 'SRD-2014', '{}')
ON CONFLICT (item_key) DO NOTHING;

-- --- 3. p. 59 Transportation: mounts and vehicles (TRANSPORT) ----------------
-- Bulk is the official DD rating — what it costs to CARRY the thing, shown for
-- reference. The frontend excludes 'transport' lines from a PC's used slots
-- (a mount carries itself; DD p. 58). Weight is NULL (not meaningful here).
INSERT INTO srd_item (item_key, name, category, cost_cp, weight, bulk, source, details) VALUES
  ('camel',          'Camel',            'TRANSPORT',   5000, NULL,   40.0, 'DD', '{"notes":"Mount — Large, speed 50 ft, carries 28 slots"}'),
  ('donkey-or-mule', 'Donkey or Mule',   'TRANSPORT',    800, NULL,   20.0, 'DD', '{"notes":"Mount — Medium, speed 40 ft, carries 26 slots"}'),
  ('elephant',       'Elephant',         'TRANSPORT',  20000, NULL,   80.0, 'DD', '{"notes":"Mount — Huge, speed 40 ft, carries 54 slots"}'),
  ('draft-horse',    'Horse, Draft',     'TRANSPORT',   5000, NULL,   40.0, 'DD', '{"notes":"Mount — Large, speed 50 ft, carries 30 slots"}'),
  ('riding-horse',   'Horse, Riding',    'TRANSPORT',   7500, NULL,   40.0, 'DD', '{"notes":"Mount — Large, speed 60 ft, carries 25 slots"}'),
  ('mastiff',        'Mastiff',          'TRANSPORT',   2500, NULL,   20.0, 'DD', '{"notes":"Mount — Medium, speed 40 ft, carries 19 slots"}'),
  ('pony',           'Pony',             'TRANSPORT',   3000, NULL,   20.0, 'DD', '{"notes":"Mount — Medium, speed 40 ft, carries 20 slots"}'),
  ('warhorse',       'Warhorse',         'TRANSPORT',  40000, NULL,   40.0, 'DD', '{"notes":"Mount — Large, speed 60 ft, carries 26 slots"}'),
  ('horse-carriage', 'Carriage (Horse)', 'TRANSPORT',  10000, NULL,  180.0, 'DD', '{"notes":"Land vehicle — Huge, carries 180 slots"}'),
  ('hand-cart',      'Cart (Hand)',      'TRANSPORT',    500, NULL,   20.0, 'DD', '{"notes":"Land vehicle — Medium, carries 20 slots"}'),
  ('horse-cart',     'Cart (Horse)',     'TRANSPORT',   1500, NULL,   60.0, 'DD', '{"notes":"Land vehicle — Large, carries 60 slots"}'),
  ('chariot',        'Chariot',          'TRANSPORT',  25000, NULL,   60.0, 'DD', '{"notes":"Land vehicle — Large, carries 60 slots"}'),
  ('hand-sled',      'Sled (Hand)',      'TRANSPORT',    500, NULL,   20.0, 'DD', '{"notes":"Land vehicle — Medium, carries 20 slots"}'),
  ('horse-sled',     'Sled (Horse)',     'TRANSPORT',   2000, NULL,  180.0, 'DD', '{"notes":"Land vehicle — Huge, carries 180 slots"}'),
  ('wagon',          'Wagon',            'TRANSPORT',   3500, NULL,  180.0, 'DD', '{"notes":"Land vehicle — Huge, carries 180 slots"}'),
  ('galley',         'Galley',           'TRANSPORT', 3000000, NULL, 4860.0, 'DD', '{"notes":"Water vehicle — Gargantuan, 4 mph, carries 4,860 slots"}'),
  ('keelboat',       'Keelboat',         'TRANSPORT', 300000, NULL,  180.0, 'DD', '{"notes":"Water vehicle — Gargantuan, 3 mph, carries 180 slots"}'),
  ('longship',       'Longship',         'TRANSPORT', 1000000, NULL, 1620.0, 'DD', '{"notes":"Water vehicle — Gargantuan, 5 mph, carries 1,620 slots"}'),
  ('rowboat',        'Rowboat',          'TRANSPORT',   5000, NULL,   60.0, 'DD', '{"notes":"Water vehicle — Large, 3 mph, carries 60 slots"}'),
  ('sailing-ship',   'Sailing Ship',     'TRANSPORT', 1000000, NULL, 1620.0, 'DD', '{"notes":"Water vehicle — Gargantuan, 5 mph, carries 1,620 slots"}'),
  ('warship',        'Warship',          'TRANSPORT', 2500000, NULL, 4860.0, 'DD', '{"notes":"Water vehicle — Gargantuan, 4 mph, carries 4,860 slots"}')
ON CONFLICT (item_key) DO NOTHING;
