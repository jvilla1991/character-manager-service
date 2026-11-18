package com.moo.charactermanagerservice.models;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Which rest a {@link SessionRestVote} is about. SHORT opens the session's
 * hit-dice window when it passes; LONG opens the long rest phase.
 */
public enum RestType {
    SHORT,
    LONG;

    /** Lenient parse for the request body: null/blank means SHORT (older clients send nothing). */
    public static RestType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SHORT;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "restType must be SHORT or LONG");
        }
    }
}
