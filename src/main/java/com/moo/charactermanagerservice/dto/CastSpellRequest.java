package com.moo.charactermanagerservice.dto;

/**
 * A player casts one of their seated PC's spells. {@code atLevel} is the slot
 * level to spend (upcasting allowed); it is ignored for cantrips.
 *
 * <p>{@code ritual} casts the spell as a ritual: no slot is spent and
 * {@code atLevel} is ignored. Only a leveled spell marked {@code "ritual":
 * true} on the sheet can be. Absent or null means an ordinary cast, so a
 * client that never sends it keeps working.
 */
public record CastSpellRequest(Long pcId, String spellName, Integer atLevel, Boolean ritual) {}
