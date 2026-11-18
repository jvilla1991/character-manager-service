package com.moo.charactermanagerservice.dto;

/** Set one seated character's share of the coin pile, in copper (0 = none). */
public record SetCoinShareRequest(Long participantId, Long coinCp) {}
