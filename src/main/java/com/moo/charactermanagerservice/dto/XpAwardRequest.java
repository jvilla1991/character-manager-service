package com.moo.charactermanagerservice.dto;

import java.util.List;

/** Body for the DM's XP awards (POST .../xp and POST .../participants/{pid}/xp).
 *  A positive {@code amount} grants XP; a negative amount is a correction. The
 *  resulting per-character total is floored at 0.
 *
 *  <p>{@code participantIds} applies to the session-wide POST .../xp only: the
 *  seated PCs the DM ticked. Null (older clients never send it) means every
 *  seated PC. */
public record XpAwardRequest(Integer amount, List<Long> participantIds) {}
