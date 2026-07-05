package com.moo.charactermanagerservice.dto;

/** The owning player appends a note to their character (sessionId optional). */
public record AddPcNoteRequest(String body, Long sessionId) {}
