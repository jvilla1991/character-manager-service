-- Catalog slice 1 of 3: SRD 2024 weapons, with list prices.
-- Prices normalized to copper (1 gp = 100 cp, 1 sp = 10 cp). `details` carries
-- weapon-specific attributes. Idempotent: re-running is a no-op on item_key.
INSERT INTO srd_item (item_key, name, category, cost_cp, weight, source, details) VALUES
-- --- Simple Melee ---
('club',            'Club',            'WEAPON',    10, 2,    'SRD-2024', '{"damage":"1d4 bludgeoning","weaponCategory":"simple","range":"melee","properties":["light"]}'),
('dagger',          'Dagger',          'WEAPON',   200, 1,    'SRD-2024', '{"damage":"1d4 piercing","weaponCategory":"simple","range":"melee","properties":["finesse","light","thrown (20/60)"]}'),
('greatclub',       'Greatclub',       'WEAPON',    20, 10,   'SRD-2024', '{"damage":"1d8 bludgeoning","weaponCategory":"simple","range":"melee","properties":["two-handed"]}'),
('handaxe',         'Handaxe',         'WEAPON',   500, 2,    'SRD-2024', '{"damage":"1d6 slashing","weaponCategory":"simple","range":"melee","properties":["light","thrown (20/60)"]}'),
('javelin',         'Javelin',         'WEAPON',    50, 2,    'SRD-2024', '{"damage":"1d6 piercing","weaponCategory":"simple","range":"melee","properties":["thrown (30/120)"]}'),
('light-hammer',    'Light Hammer',    'WEAPON',   200, 2,    'SRD-2024', '{"damage":"1d4 bludgeoning","weaponCategory":"simple","range":"melee","properties":["light","thrown (20/60)"]}'),
('mace',            'Mace',            'WEAPON',   500, 4,    'SRD-2024', '{"damage":"1d6 bludgeoning","weaponCategory":"simple","range":"melee","properties":[]}'),
('quarterstaff',    'Quarterstaff',    'WEAPON',    20, 4,    'SRD-2024', '{"damage":"1d6 bludgeoning","weaponCategory":"simple","range":"melee","properties":["versatile (1d8)"]}'),
('sickle',          'Sickle',          'WEAPON',   100, 2,    'SRD-2024', '{"damage":"1d4 slashing","weaponCategory":"simple","range":"melee","properties":["light"]}'),
('spear',           'Spear',           'WEAPON',   100, 3,    'SRD-2024', '{"damage":"1d6 piercing","weaponCategory":"simple","range":"melee","properties":["thrown (20/60)","versatile (1d8)"]}'),
-- --- Simple Ranged ---
('dart',            'Dart',            'WEAPON',     5, 0.25, 'SRD-2024', '{"damage":"1d4 piercing","weaponCategory":"simple","range":"ranged","properties":["finesse","thrown (20/60)"]}'),
('light-crossbow',  'Light Crossbow',  'WEAPON',  2500, 5,    'SRD-2024', '{"damage":"1d8 piercing","weaponCategory":"simple","range":"ranged","properties":["ammunition (80/320)","loading","two-handed"]}'),
('shortbow',        'Shortbow',        'WEAPON',  2500, 2,    'SRD-2024', '{"damage":"1d6 piercing","weaponCategory":"simple","range":"ranged","properties":["ammunition (80/320)","two-handed"]}'),
('sling',           'Sling',           'WEAPON',    10, 0,    'SRD-2024', '{"damage":"1d4 bludgeoning","weaponCategory":"simple","range":"ranged","properties":["ammunition (30/120)"]}'),
-- --- Martial Melee ---
('battleaxe',       'Battleaxe',       'WEAPON',  1000, 4,    'SRD-2024', '{"damage":"1d8 slashing","weaponCategory":"martial","range":"melee","properties":["versatile (1d10)"]}'),
('flail',           'Flail',           'WEAPON',  1000, 2,    'SRD-2024', '{"damage":"1d8 bludgeoning","weaponCategory":"martial","range":"melee","properties":[]}'),
('glaive',          'Glaive',          'WEAPON',  2000, 6,    'SRD-2024', '{"damage":"1d10 slashing","weaponCategory":"martial","range":"melee","properties":["heavy","reach","two-handed"]}'),
('greataxe',        'Greataxe',        'WEAPON',  3000, 7,    'SRD-2024', '{"damage":"1d12 slashing","weaponCategory":"martial","range":"melee","properties":["heavy","two-handed"]}'),
('greatsword',      'Greatsword',      'WEAPON',  5000, 6,    'SRD-2024', '{"damage":"2d6 slashing","weaponCategory":"martial","range":"melee","properties":["heavy","two-handed"]}'),
('halberd',         'Halberd',         'WEAPON',  2000, 6,    'SRD-2024', '{"damage":"1d10 slashing","weaponCategory":"martial","range":"melee","properties":["heavy","reach","two-handed"]}'),
('lance',           'Lance',           'WEAPON',  1000, 6,    'SRD-2024', '{"damage":"1d12 piercing","weaponCategory":"martial","range":"melee","properties":["heavy","reach","special"]}'),
('longsword',       'Longsword',       'WEAPON',  1500, 3,    'SRD-2024', '{"damage":"1d8 slashing","weaponCategory":"martial","range":"melee","properties":["versatile (1d10)"]}'),
('maul',            'Maul',            'WEAPON',  1000, 10,   'SRD-2024', '{"damage":"2d6 bludgeoning","weaponCategory":"martial","range":"melee","properties":["heavy","two-handed"]}'),
('morningstar',     'Morningstar',     'WEAPON',  1500, 4,    'SRD-2024', '{"damage":"1d8 piercing","weaponCategory":"martial","range":"melee","properties":[]}'),
('pike',            'Pike',            'WEAPON',   500, 18,   'SRD-2024', '{"damage":"1d10 piercing","weaponCategory":"martial","range":"melee","properties":["heavy","reach","two-handed"]}'),
('rapier',          'Rapier',          'WEAPON',  2500, 2,    'SRD-2024', '{"damage":"1d8 piercing","weaponCategory":"martial","range":"melee","properties":["finesse"]}'),
('scimitar',        'Scimitar',        'WEAPON',  2500, 3,    'SRD-2024', '{"damage":"1d6 slashing","weaponCategory":"martial","range":"melee","properties":["finesse","light"]}'),
('shortsword',      'Shortsword',      'WEAPON',  1000, 2,    'SRD-2024', '{"damage":"1d6 piercing","weaponCategory":"martial","range":"melee","properties":["finesse","light"]}'),
('trident',         'Trident',         'WEAPON',   500, 4,    'SRD-2024', '{"damage":"1d6 piercing","weaponCategory":"martial","range":"melee","properties":["thrown (20/60)","versatile (1d8)"]}'),
('war-pick',        'War Pick',        'WEAPON',   500, 2,    'SRD-2024', '{"damage":"1d8 piercing","weaponCategory":"martial","range":"melee","properties":[]}'),
('warhammer',       'Warhammer',       'WEAPON',  1500, 2,    'SRD-2024', '{"damage":"1d8 bludgeoning","weaponCategory":"martial","range":"melee","properties":["versatile (1d10)"]}'),
('whip',            'Whip',            'WEAPON',   200, 3,    'SRD-2024', '{"damage":"1d4 slashing","weaponCategory":"martial","range":"melee","properties":["finesse","reach"]}'),
-- --- Martial Ranged ---
('blowgun',         'Blowgun',         'WEAPON',  1000, 1,    'SRD-2024', '{"damage":"1 piercing","weaponCategory":"martial","range":"ranged","properties":["ammunition (25/100)","loading"]}'),
('hand-crossbow',   'Hand Crossbow',   'WEAPON',  7500, 3,    'SRD-2024', '{"damage":"1d6 piercing","weaponCategory":"martial","range":"ranged","properties":["ammunition (30/120)","light","loading"]}'),
('heavy-crossbow',  'Heavy Crossbow',  'WEAPON',  5000, 18,   'SRD-2024', '{"damage":"1d10 piercing","weaponCategory":"martial","range":"ranged","properties":["ammunition (100/400)","heavy","loading","two-handed"]}'),
('longbow',         'Longbow',         'WEAPON',  5000, 2,    'SRD-2024', '{"damage":"1d8 piercing","weaponCategory":"martial","range":"ranged","properties":["ammunition (150/600)","heavy","two-handed"]}'),
('net',             'Net',             'WEAPON',   100, 3,    'SRD-2024', '{"damage":"—","weaponCategory":"martial","range":"ranged","properties":["thrown (5/15)","special"]}')
ON CONFLICT (item_key) DO NOTHING;
