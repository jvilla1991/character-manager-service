-- One long rest every 24 hours, counted on the campaign clock.
--
-- The campaign remembers its last FINISHED long rest (an interrupted one does
-- not count) as a small JSON record:
--
--   {"timeOfDay":"morning","segmentsSince":0}
--
-- timeOfDay is the clock segment the rest finished in (after any clock advance
-- that finish made); segmentsSince counts the segments the clock has moved
-- since. Dates on the campaign clock are free text, so two clock positions
-- cannot be subtracted — the counter is what measures the wait. A new long
-- rest may start once two segments (sixteen hours) have passed.
--
-- NULL = no limit: no long rest finished yet, it finished on a clock that was
-- never set, or the DM has since set the date by hand.
ALTER TABLE campaign
    ADD COLUMN IF NOT EXISTS last_long_rest TEXT;
