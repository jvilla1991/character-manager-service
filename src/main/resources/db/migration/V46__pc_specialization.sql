-- Generalize the Death Knight "rune" (V45) into a hero-class "specialization".
-- Each World of Warcraft hero subclass has its own specialization set (Death Knight:
-- Blood/Frost/Unholy; Demon Hunter: Devourer/Havoc/Vengeance), so the allowed values
-- depend on the subclass and are validated in the service layer — no DB CHECK here.
ALTER TABLE pc ADD COLUMN specialization VARCHAR(20);

UPDATE pc SET specialization = rune WHERE rune IS NOT NULL;

-- Postgres named V45's inline column check pc_rune_check; dropping the column would
-- drop it too, but remove it explicitly so the intent is clear.
ALTER TABLE pc DROP CONSTRAINT IF EXISTS pc_rune_check;
ALTER TABLE pc DROP COLUMN rune;
