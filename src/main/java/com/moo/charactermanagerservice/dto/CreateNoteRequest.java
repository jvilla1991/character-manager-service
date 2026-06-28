package com.moo.charactermanagerservice.dto;

/**
 * Body for POST /api/v1/campaign/{id}/notes. {@code sessionId} is optional — the
 * client sends it when the note is taken from inside a live session, omits it from
 * the campaign menu.
 */
public record CreateNoteRequest(String body, Long sessionId) {}
