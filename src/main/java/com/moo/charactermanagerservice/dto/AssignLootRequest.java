package com.moo.charactermanagerservice.dto;

/**
 * Move {@code qty} of a pool line during the party split. {@code
 * fromParticipantId} / {@code toParticipantId} name seated characters; null
 * means the pile. Pile → character hands an item out, character → pile
 * returns it, character → character re-assigns it.
 */
public record AssignLootRequest(Long lootItemId, Long fromParticipantId, Long toParticipantId, Integer qty) {}
