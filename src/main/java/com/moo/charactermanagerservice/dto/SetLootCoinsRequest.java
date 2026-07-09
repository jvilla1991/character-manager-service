package com.moo.charactermanagerservice.dto;

/** Set a loot coin pile, in gold (fractions allowed — 0.5 gp = 5 sp). */
public record SetLootCoinsRequest(Double coinGp) {}
