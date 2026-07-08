-- Ordered weekday names the DM defined for the campaign's calendar, set at
-- creation or from the campaign dashboard. JSON: ["Sul","Mol","Zol",...].
-- NULL until the DM defines one — the clock then keeps its free-text weekday
-- whose repetition drives the week counter.
ALTER TABLE campaign ADD COLUMN IF NOT EXISTS week_days TEXT;
