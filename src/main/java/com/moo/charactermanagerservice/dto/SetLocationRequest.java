package com.moo.charactermanagerservice.dto;

/**
 * DM sets the party's current location from Session Mode. {@code name} is free
 * text (the place's name); {@code type} is one of Settlement | Wilderness |
 * Dungeon. A blank name with a valid type is allowed (an unnamed spot).
 */
public record SetLocationRequest(String name, String type) {}
