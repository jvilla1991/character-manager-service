package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * What a hero chooses as a long rest ends. Both parts are optional — null
 * means "leave it as it is".
 *
 * <p>{@code preparedSpells} is the complete list of leveled spells (by name)
 * the hero will have prepared after the rest; cantrips are never part of it.
 * {@code runeWeapon} is the name of the melee weapon a Death Knight's rune is
 * inscribed on.
 *
 * <p>The choices travel with the player's Ready, wait on their seat, and are
 * applied with recovery when the rest finishes. Un-readying discards them.
 */
public record LongRestChoices(List<String> preparedSpells, String runeWeapon) {

    public boolean isEmpty() {
        return preparedSpells == null && (runeWeapon == null || runeWeapon.isBlank());
    }
}
