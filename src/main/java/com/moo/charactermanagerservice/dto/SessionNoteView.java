package com.moo.charactermanagerservice.dto;

import com.moo.charactermanagerservice.models.SessionNote;

import java.time.Instant;

/**
 * Response shape for a DM session note. {@code sessionId} is null when the note
 * was added outside a live session.
 */
public record SessionNoteView(
        Long id,
        Long campaignId,
        Long sessionId,
        String body,
        Instant createdAt
) {
    public static SessionNoteView from(SessionNote note) {
        return new SessionNoteView(
                note.getId(),
                note.getCampaignId(),
                note.getSessionId(),
                note.getBody(),
                note.getCreatedAt());
    }
}
