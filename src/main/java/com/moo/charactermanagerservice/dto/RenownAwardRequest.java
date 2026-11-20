package com.moo.charactermanagerservice.dto;

import java.util.List;

/**
 * Body for the DM's session-wide renown reward (POST /session/{id}/renown):
 * move each ticked character's score with {@code factionId} by {@code amount}
 * (any non-zero whole number from -200 to 200; each result is clamped to
 * -100..100). {@code participantIds} are the seated PCs the DM ticked; null
 * means every seated PC.
 */
public record RenownAwardRequest(Long factionId, Integer amount, List<Long> participantIds) {}
