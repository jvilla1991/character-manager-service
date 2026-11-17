-- Death Knight (Oath of the Ebon Blade) specialization chosen via Runeforging.
-- Nullable: only Ebon Blade paladins carry a rune (enforced in the service layer).
ALTER TABLE pc ADD COLUMN rune VARCHAR(10) CHECK (rune IN ('Blood', 'Frost', 'Unholy'));
