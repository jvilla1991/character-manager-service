package com.moo.charactermanagerservice.dto;

import java.util.Map;

/**
 * The spellcasting numbers of a level-up preview as they would be with one of the subclass
 * options chosen: {@link LevelUpPreview#spellcastingBySubclass}. Each field replaces the
 * preview's field of the same name once the player picks that subclass.
 *
 * @param newSpellSlots    {@code spellLevel -> maxSlots} at the new level (table plus the DM's bonus lines)
 * @param newCantripsKnown cantrips known at the new level
 * @param newSpellsKnown   prepared spells at the new level
 */
public record SubclassSpellcasting(
        Map<Integer, Integer> newSpellSlots,
        int newCantripsKnown,
        int newSpellsKnown
) {}
