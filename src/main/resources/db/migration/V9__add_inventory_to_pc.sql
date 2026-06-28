-- Structured character inventory. Mirrors the spell-storage pattern (V2): a
-- JSON-array-of-objects TEXT column on the pc row, read/written whole by the
-- service layer and (de)serialized on the frontend. This is the canonical home
-- for purchased items; the legacy `weapons`/`gear` columns are left untouched
-- and may be consolidated into `inventory` in a later phase.
ALTER TABLE pc ADD COLUMN IF NOT EXISTS inventory TEXT;
