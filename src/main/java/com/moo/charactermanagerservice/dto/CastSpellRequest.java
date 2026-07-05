package com.moo.charactermanagerservice.dto;

/**
 * A player casts one of their seated PC's spells. {@code atLevel} is the slot
 * level to spend (upcasting allowed); it is ignored for cantrips.
 */
public record CastSpellRequest(Long pcId, String spellName, Integer atLevel) {}
