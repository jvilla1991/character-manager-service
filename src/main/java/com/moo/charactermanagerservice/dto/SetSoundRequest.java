package com.moo.charactermanagerservice.dto;

/**
 * Body of {@code PUT /session/{id}/sound}. Sets the encounter-level turn-cue
 * key pushed to every client; null clears it (silent encounter).
 */
public record SetSoundRequest(String turnSound) {}
