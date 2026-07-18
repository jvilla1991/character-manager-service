package com.moo.charactermanagerservice.dto;

/** Edit a DM session note's body (must not be blank). */
public record UpdateNoteRequest(String body) {}
