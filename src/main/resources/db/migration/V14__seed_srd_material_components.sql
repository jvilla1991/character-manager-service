-- Catalog slice 3 of 3: costed material components for spells of 3rd level and
-- below (SRD 2024). Hand-curated — these gp-valued components are sparse and
-- embedded in spell text, so this is a deliberate set, not a bulk import.
--
-- `cost_cp` is the component's gp value in copper (1 gp = 100 cp). `details`
-- records the spell that needs it, its level, and crucially whether the spell
-- CONSUMES the component on cast (consumedOnCast) vs. merely requiring it
-- (reusable). Buying a component only adds it to inventory; consumption on cast
-- is a later feature. Idempotent on item_key.
INSERT INTO srd_item (item_key, name, category, cost_cp, weight, source, details) VALUES
-- --- 1st level ---
('mc-chromatic-orb',   'Diamond (Chromatic Orb)',                'MATERIAL_COMPONENT',  5000, NULL, 'SRD-2024', '{"spell":"Chromatic Orb","spellLevel":1,"gpValue":50,"consumedOnCast":false,"material":"a diamond worth 50+ GP"}'),
('mc-find-familiar',   'Charcoal, Incense & Herbs (Find Familiar)','MATERIAL_COMPONENT', 1000, NULL, 'SRD-2024', '{"spell":"Find Familiar","spellLevel":1,"gpValue":10,"consumedOnCast":true,"material":"charcoal, incense, and herbs worth 10+ GP, consumed by the spell"}'),
('mc-identify',        'Pearl (Identify)',                       'MATERIAL_COMPONENT', 10000, NULL, 'SRD-2024', '{"spell":"Identify","spellLevel":1,"gpValue":100,"consumedOnCast":false,"material":"a pearl worth 100+ GP"}'),
-- --- 2nd level ---
('mc-augury',          'Marked Sticks or Bones (Augury)',        'MATERIAL_COMPONENT',  2500, NULL, 'SRD-2024', '{"spell":"Augury","spellLevel":2,"gpValue":25,"consumedOnCast":false,"material":"specially marked sticks, bones, or cards worth 25+ GP"}'),
('mc-continual-flame', 'Ruby Dust (Continual Flame)',            'MATERIAL_COMPONENT',  5000, NULL, 'SRD-2024', '{"spell":"Continual Flame","spellLevel":2,"gpValue":50,"consumedOnCast":true,"material":"ruby dust worth 50+ GP, which the spell consumes"}'),
('mc-magic-mouth',     'Jade Dust (Magic Mouth)',                'MATERIAL_COMPONENT',  1000, NULL, 'SRD-2024', '{"spell":"Magic Mouth","spellLevel":2,"gpValue":10,"consumedOnCast":true,"material":"jade dust worth 10+ GP, which the spell consumes"}'),
('mc-arcane-lock',     'Gold Dust (Arcane Lock)',                'MATERIAL_COMPONENT',  2500, NULL, 'SRD-2024', '{"spell":"Arcane Lock","spellLevel":2,"gpValue":25,"consumedOnCast":true,"material":"gold dust worth 25+ GP, which the spell consumes"}'),
('mc-warding-bond',    'Platinum Rings, pair (Warding Bond)',    'MATERIAL_COMPONENT', 10000, NULL, 'SRD-2024', '{"spell":"Warding Bond","spellLevel":2,"gpValue":100,"consumedOnCast":false,"material":"a pair of platinum rings worth 50+ GP each, worn by you and the target"}'),
-- --- 3rd level ---
('mc-clairvoyance',    'Scrying Focus (Clairvoyance)',           'MATERIAL_COMPONENT', 10000, NULL, 'SRD-2024', '{"spell":"Clairvoyance","spellLevel":3,"gpValue":100,"consumedOnCast":false,"material":"a focus worth 100+ GP (a jeweled horn for hearing or a glass eye for seeing)"}'),
('mc-glyph-of-warding','Powdered Diamond (Glyph of Warding)',    'MATERIAL_COMPONENT', 20000, NULL, 'SRD-2024', '{"spell":"Glyph of Warding","spellLevel":3,"gpValue":200,"consumedOnCast":true,"material":"incense and powdered diamond worth 200+ GP, which the spell consumes"}'),
('mc-magic-circle',    'Powdered Silver & Iron (Magic Circle)',  'MATERIAL_COMPONENT', 10000, NULL, 'SRD-2024', '{"spell":"Magic Circle","spellLevel":3,"gpValue":100,"consumedOnCast":true,"material":"salt and powdered silver and iron worth 100+ GP, which the spell consumes"}'),
('mc-nondetection',    'Diamond Dust (Nondetection)',            'MATERIAL_COMPONENT',  2500, NULL, 'SRD-2024', '{"spell":"Nondetection","spellLevel":3,"gpValue":25,"consumedOnCast":true,"material":"a pinch of diamond dust worth 25+ GP, which the spell consumes"}'),
('mc-revivify',        'Diamonds (Revivify)',                    'MATERIAL_COMPONENT', 30000, NULL, 'SRD-2024', '{"spell":"Revivify","spellLevel":3,"gpValue":300,"consumedOnCast":true,"material":"diamonds worth 300+ GP, which the spell consumes"}')
ON CONFLICT (item_key) DO NOTHING;
