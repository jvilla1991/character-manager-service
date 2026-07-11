package com.moo.charactermanagerservice.dto;

/**
 * Body of {@code PUT /pc/{id}/level-grant} (DM only): {@code granted} = true
 * gives the character a pending level-up (consumed when they level); false
 * revokes an unconsumed grant.
 */
public record LevelGrantRequest(Boolean granted) {}
