-- The game uses three coins: copper, silver and gold. V49 already turned
-- stored platinum into gold and electrum into silver; this pass removes the
-- leftover "pp" and "ep" keys so every purse is exactly {cp, sp, gp}. Any
-- platinum or electrum written since V49 is converted first (1 pp = 10 gp,
-- 1 ep = 5 sp), so no value is lost. Rows are rewritten one at a time; a purse
-- that can't be read as a JSON object is left exactly as it was (logged as a
-- NOTICE) rather than failing the migration and blocking the boot.
DO $$
DECLARE
    r  RECORD;
    j  jsonb;
    pp numeric;
    ep numeric;
BEGIN
    FOR r IN SELECT id, coins FROM pc WHERE coins IS NOT NULL AND coins <> '' LOOP
        BEGIN
            j := r.coins::jsonb;
            IF jsonb_typeof(j) <> 'object' OR (j->'pp' IS NULL AND j->'ep' IS NULL) THEN
                CONTINUE;
            END IF;
            pp := COALESCE(NULLIF(j->>'pp', '')::numeric, 0);
            ep := COALESCE(NULLIF(j->>'ep', '')::numeric, 0);
            j := (j - 'pp' - 'ep') || jsonb_build_object(
                'gp', COALESCE(NULLIF(j->>'gp', '')::numeric, 0) + 10 * pp,
                'sp', COALESCE(NULLIF(j->>'sp', '')::numeric, 0) + 5 * ep);
            UPDATE pc SET coins = j::text WHERE id = r.id;
        EXCEPTION WHEN others THEN
            RAISE NOTICE 'V50: left pc % coins unchanged (unreadable purse): %', r.id, SQLERRM;
        END;
    END LOOP;
END $$;
