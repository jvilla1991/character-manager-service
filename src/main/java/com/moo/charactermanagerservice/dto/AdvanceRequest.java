package com.moo.charactermanagerservice.dto;

/**
 * Body of {@code POST /session/{id}/advance}. The caller echoes the participant
 * ID they believe is currently active (from their latest snapshot); the server
 * rejects the advance with 409 if it no longer matches, so two racing advances
 * (DM Next vs. a player's End Turn) can never move the turn twice.
 */
public record AdvanceRequest(Long expectedActiveParticipantId) {}
