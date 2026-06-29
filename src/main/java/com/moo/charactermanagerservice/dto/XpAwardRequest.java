package com.moo.charactermanagerservice.dto;

/** Body for the DM's XP awards (POST .../xp and POST .../participants/{pid}/xp).
 *  A positive {@code amount} grants XP; a negative amount is a correction. The
 *  resulting per-character total is floored at 0. */
public record XpAwardRequest(Integer amount) {}
