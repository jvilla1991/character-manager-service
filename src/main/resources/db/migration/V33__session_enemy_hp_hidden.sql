-- Third enemy-visibility state: players may see enemy combatants but not
-- their health. Complements combat_session.enemies_hidden (which omits enemy
-- rows from player snapshots entirely): when enemies are visible and
-- enemy_hp_hidden is TRUE, players get the rows with the HP fields nulled
-- server-side. Existing sessions keep today's behavior (FALSE = full HP).
ALTER TABLE combat_session
    ADD COLUMN IF NOT EXISTS enemy_hp_hidden BOOLEAN NOT NULL DEFAULT FALSE;
