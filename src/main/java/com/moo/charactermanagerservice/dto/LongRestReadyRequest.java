package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * A player marks their own seated PC ready (or not ready) for the long rest.
 * A Ready may carry the hero's choices: {@code preparedSpells} (the complete
 * list of leveled spells to have prepared afterwards) and {@code runeWeapon}
 * (the melee weapon a Death Knight's rune goes on). Either may be absent to
 * leave that part as it is. They are ignored when {@code ready} is false.
 */
public record LongRestReadyRequest(Long pcId, Boolean ready, List<String> preparedSpells, String runeWeapon) {}
