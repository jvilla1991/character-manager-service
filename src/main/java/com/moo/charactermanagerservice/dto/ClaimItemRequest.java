package com.moo.charactermanagerservice.dto;

/** A player claims {@code qty} of a loot line into their character's inventory. */
public record ClaimItemRequest(Long pcId, Long lootItemId, Integer qty) {}
