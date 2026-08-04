package com.moo.charactermanagerservice.dto;

/**
 * Body of a rest-vote answer. Used by both the player cast endpoint (yes/no
 * ballot) and the DM override endpoint (true = pass the vote now, false =
 * fail it now). {@code vote} is required — 400 when absent.
 */
public record RestVoteCastRequest(Boolean vote) {}
