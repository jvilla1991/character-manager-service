package com.moo.charactermanagerservice.dto;

/**
 * A spell a class or subclass feature always keeps prepared (a Paladin's Find Steed from
 * Faithful Steed, an Oath of Devotion spell). The level-up preview lists the ones the character
 * is owed so the app can show them and send the full spell along with the player's picks; it
 * never counts against the picks or the prepared-spell limit.
 *
 * @param name      the spell's name
 * @param grantedBy the feature that grants it, shown beside the spell on the sheet
 */
public record SpellGrant(String name, String grantedBy) {}
