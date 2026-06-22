package com.moo.charactermanagerservice.dto;

/** Body for POST /api/v1/session/{id}/join — a player seats one of their PCs. */
public record JoinSessionRequest(Long pcId) {}
