-- Catalog slice 2 of 3: SRD 2024 armor, with list prices.
-- Prices normalized to copper (1 gp = 100 cp). `details` carries armor-specific
-- attributes: armorCategory, a display armorClass string, the structured AC
-- (baseAc + how Dex applies), Strength requirement, and stealth disadvantage.
-- Idempotent on item_key.
INSERT INTO srd_item (item_key, name, category, cost_cp, weight, source, details) VALUES
-- --- Light Armor (AC = base + full Dex) ---
('padded',          'Padded Armor',    'ARMOR',     500, 8,  'SRD-2024', '{"armorCategory":"light","armorClass":"11 + Dex modifier","baseAc":11,"dex":"full","stealthDisadvantage":true}'),
('leather',         'Leather Armor',   'ARMOR',    1000, 10, 'SRD-2024', '{"armorCategory":"light","armorClass":"11 + Dex modifier","baseAc":11,"dex":"full","stealthDisadvantage":false}'),
('studded-leather', 'Studded Leather', 'ARMOR',    4500, 13, 'SRD-2024', '{"armorCategory":"light","armorClass":"12 + Dex modifier","baseAc":12,"dex":"full","stealthDisadvantage":false}'),
-- --- Medium Armor (AC = base + Dex, max +2) ---
('hide',            'Hide Armor',      'ARMOR',    1000, 12, 'SRD-2024', '{"armorCategory":"medium","armorClass":"12 + Dex modifier (max 2)","baseAc":12,"dex":"max2","stealthDisadvantage":false}'),
('chain-shirt',     'Chain Shirt',     'ARMOR',    5000, 20, 'SRD-2024', '{"armorCategory":"medium","armorClass":"13 + Dex modifier (max 2)","baseAc":13,"dex":"max2","stealthDisadvantage":false}'),
('scale-mail',      'Scale Mail',      'ARMOR',    5000, 45, 'SRD-2024', '{"armorCategory":"medium","armorClass":"14 + Dex modifier (max 2)","baseAc":14,"dex":"max2","stealthDisadvantage":true}'),
('breastplate',     'Breastplate',     'ARMOR',   40000, 20, 'SRD-2024', '{"armorCategory":"medium","armorClass":"14 + Dex modifier (max 2)","baseAc":14,"dex":"max2","stealthDisadvantage":false}'),
('half-plate',      'Half Plate Armor','ARMOR',   75000, 40, 'SRD-2024', '{"armorCategory":"medium","armorClass":"15 + Dex modifier (max 2)","baseAc":15,"dex":"max2","stealthDisadvantage":true}'),
-- --- Heavy Armor (fixed AC; some require Strength) ---
('ring-mail',       'Ring Mail',       'ARMOR',    3000, 40, 'SRD-2024', '{"armorCategory":"heavy","armorClass":"14","baseAc":14,"dex":"none","stealthDisadvantage":true}'),
('chain-mail',      'Chain Mail',      'ARMOR',    7500, 55, 'SRD-2024', '{"armorCategory":"heavy","armorClass":"16","baseAc":16,"dex":"none","strengthRequirement":13,"stealthDisadvantage":true}'),
('splint',          'Splint Armor',    'ARMOR',   20000, 60, 'SRD-2024', '{"armorCategory":"heavy","armorClass":"17","baseAc":17,"dex":"none","strengthRequirement":15,"stealthDisadvantage":true}'),
('plate',           'Plate Armor',     'ARMOR',  150000, 65, 'SRD-2024', '{"armorCategory":"heavy","armorClass":"18","baseAc":18,"dex":"none","strengthRequirement":15,"stealthDisadvantage":true}'),
-- --- Shield ---
('shield',          'Shield',          'ARMOR',    1000, 6,  'SRD-2024', '{"armorCategory":"shield","armorClass":"+2","baseAc":2,"dex":"none","stealthDisadvantage":false}')
ON CONFLICT (item_key) DO NOTHING;
