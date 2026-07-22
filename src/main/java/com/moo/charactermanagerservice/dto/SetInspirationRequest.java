package com.moo.charactermanagerservice.dto;

/**
 * DM sets a character's inspiration meter to an explicit number of pips, rather
 * than nudging it by one. The sheet's pips are clickable, so the DM can both
 * raise and lower the meter in a single click; a request for the full meter
 * ({@link com.moo.charactermanagerservice.services.PCService#INSPIRATION_METER_SIZE})
 * empties it and grants Heroic Inspiration instead.
 */
public record SetInspirationRequest(Integer pips) {}
