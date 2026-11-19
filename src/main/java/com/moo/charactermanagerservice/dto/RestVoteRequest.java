package com.moo.charactermanagerservice.dto;

/**
 * Optional body of {@code POST /session/{id}/rest-vote}. {@code restType} is
 * SHORT or LONG; a missing body or field means SHORT, so clients that predate
 * the long rest keep working.
 */
public record RestVoteRequest(String restType) {}
