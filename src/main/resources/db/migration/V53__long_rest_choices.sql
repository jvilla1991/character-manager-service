-- Long rest choices (slice 2 of the long rest): prepared spells and the Death
-- Knight's rune weapon are chosen on the player's long rest screen and applied
-- with recovery when the rest finishes.

-- The melee weapon a Death Knight's rune is inscribed on, by NAME: weapons on
-- a character (pc.weapons / pc.inventory JSON) have no id and are told apart
-- by name. NULL = no weapon inscribed. A name that no longer matches a melee
-- weapon the character has is treated as unset by the application.
ALTER TABLE pc
    ADD COLUMN IF NOT EXISTS rune_weapon TEXT;

-- What a seated player chose with their Ready, waiting for the rest to
-- finish: JSON {"preparedSpells":[...],"runeWeapon":"..."}. NULL = nothing
-- chosen. Cleared when the player un-readies, when a phase opens, and once the
-- rest finishes. It lives beside long_rest_ready (V52) and cascades away with
-- the seat.
ALTER TABLE session_participant
    ADD COLUMN IF NOT EXISTS long_rest_choices TEXT;
