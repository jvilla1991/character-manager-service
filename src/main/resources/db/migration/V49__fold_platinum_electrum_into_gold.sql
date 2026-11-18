-- The character sheet's purse shows only copper, silver and gold (electrum
-- and platinum left the display on 2026-11-13), but every shop sale, purchase
-- and loot payout re-minted the whole purse greedily into platinum — so gold a
-- character received could vanish from view. CoinPurse now mints change in
-- gp/sp/cp only; this one-time pass folds what is already stored:
--   platinum → gold   (1 pp = 10 gp)
--   electrum → silver (1 ep = 5 sp)
-- Total wealth is unchanged. Rows are converted one at a time; a purse that
-- can't be read as a JSON object is left exactly as it was (logged as a
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
            IF jsonb_typeof(j) <> 'object' THEN
                CONTINUE;
            END IF;
            pp := COALESCE(NULLIF(j->>'pp', '')::numeric, 0);
            ep := COALESCE(NULLIF(j->>'ep', '')::numeric, 0);
            IF pp = 0 AND ep = 0 THEN
                CONTINUE;
            END IF;
            j := j || jsonb_build_object(
                'gp', COALESCE(NULLIF(j->>'gp', '')::numeric, 0) + 10 * pp,
                'sp', COALESCE(NULLIF(j->>'sp', '')::numeric, 0) + 5 * ep,
                'pp', 0,
                'ep', 0);
            UPDATE pc SET coins = j::text WHERE id = r.id;
        EXCEPTION WHEN others THEN
            RAISE NOTICE 'V49: left pc % coins unchanged (unreadable purse): %', r.id, SQLERRM;
        END;
    END LOOP;
END $$;
