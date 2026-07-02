-- Initiative Tracker: replace the positional turn pointer with a stable
-- participant-ID pointer. An index-based pointer silently reassigns whose turn
-- it is whenever the list re-sorts (initiative entered mid-encounter, enemy
-- added); an ID-based pointer never moves on re-sort, which is what makes the
-- "a combatant sorted above the current turn acts next round" rule structural
-- instead of special-cased.
--
-- ON DELETE SET NULL is a backstop only — SessionService advances the pointer
-- off a combatant before deleting them.
ALTER TABLE combat_session
    ADD COLUMN IF NOT EXISTS current_turn_participant_id
        BIGINT REFERENCES session_participant (id) ON DELETE SET NULL;

-- Sessions are ephemeral (4h idle TTL, ENDED rows are history only), so the old
-- positional column is dropped outright rather than kept for back-compat.
ALTER TABLE combat_session DROP COLUMN IF EXISTS current_turn_index;
