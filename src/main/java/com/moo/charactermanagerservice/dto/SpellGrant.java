package com.moo.charactermanagerservice.dto;

/**
 * A spell a class feature always keeps prepared, gained at a level-up (a Paladin's Find Steed
 * from Faithful Steed). The level-up preview lists these so the app can show them and send the
 * full spell along with the player's picks; it never counts against the picks or the
 * prepared-spell limit.
 *
 * @param name      the spell's name
 * @param grantedBy the class feature that grants it, shown beside the spell on the sheet
 */
public record SpellGrant(String name, String grantedBy) {}
