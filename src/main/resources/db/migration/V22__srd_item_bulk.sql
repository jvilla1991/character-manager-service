-- Object bulk (Giffyglyph's Darker Dungeons ch. 10) for the slot-based
-- inventory campaign variant. Values are 0.2 / 1 / 2 / 3 / 6 / 9; used to
-- stamp `bulk` onto purchased/converted inventory lines. Official per-item
-- ratings from the DD equipment tables (p. 59). Idempotent: re-running sets
-- the same values.
ALTER TABLE srd_item ADD COLUMN IF NOT EXISTS bulk NUMERIC(4,1);

UPDATE srd_item AS s SET bulk = v.bulk
FROM (VALUES
  -- weapons (DD p. 59)
  ('battleaxe',      3.0),
  ('blowgun',        1.0),
  ('club',           2.0),
  ('dagger',         1.0),
  ('dart',           0.2),
  ('flail',          2.0),
  ('glaive',         3.0),
  ('greataxe',       3.0),
  ('greatclub',      3.0),
  ('greatsword',     3.0),
  ('halberd',        3.0),
  ('hand-crossbow',  1.0),
  ('handaxe',        2.0),
  ('heavy-crossbow', 3.0),
  ('javelin',        1.0),  -- DD lists 'Javelin (5)' at 3 bulk; catalog row is per-javelin
  ('lance',          3.0),
  ('light-crossbow', 2.0),
  ('light-hammer',   1.0),
  ('longbow',        3.0),
  ('longsword',      3.0),
  ('mace',           2.0),
  ('maul',           3.0),
  ('morningstar',    2.0),
  ('net',            1.0),
  ('pike',           3.0),
  ('quarterstaff',   3.0),
  ('rapier',         2.0),
  ('scimitar',       2.0),
  ('shortbow',       2.0),
  ('shortsword',     2.0),
  ('sickle',         1.0),
  ('sling',          1.0),
  ('spear',          3.0),
  ('trident',        3.0),
  ('war-pick',       2.0),
  ('warhammer',      3.0),
  ('whip',           1.0),
  -- armor (DD p. 59: light L/3, medium XL/6, heavy XXL/9, shield M/2)
  ('padded',          3.0),
  ('leather',         3.0),
  ('studded-leather', 3.0),
  ('hide',            6.0),
  ('chain-shirt',     6.0),
  ('scale-mail',      6.0),
  ('breastplate',     6.0),
  ('half-plate',      6.0),
  ('ring-mail',       9.0),
  ('chain-mail',      9.0),
  ('splint',          9.0),
  ('plate',           9.0),
  ('shield',          2.0)
) AS v(item_key, bulk)
WHERE s.item_key = v.item_key;

-- Material components are pinches of dust, gems, rings — Tiny (0.2) — except
-- the two hand-sized ones (augury sticks/bones, clairvoyance focus): Small (1).
UPDATE srd_item SET bulk = 0.2 WHERE category = 'MATERIAL_COMPONENT';
UPDATE srd_item SET bulk = 1.0 WHERE item_key IN ('mc-augury', 'mc-clairvoyance');
