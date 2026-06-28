package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A single DM session note: a campaign-scoped log entry the owning Dungeon Master
 * adds out of session (from the campaign menu) or live during a session. Owned by
 * {@code dmUserId}; {@code sessionId} optionally records the {@link CombatSession}
 * it was taken in (nulled if that session is later deleted). Column names map from
 * camelCase via the default snake_case naming strategy, matching {@link Campaign}.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_note")
public class SessionNote implements Serializable {

    public SessionNote() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long campaignId;

    private Long sessionId;   // nullable — null when added outside a live session

    private UUID dmUserId;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Setters ---

    public void setId(Long id) { this.id = id; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setDmUserId(UUID dmUserId) { this.dmUserId = dmUserId; }
    public void setBody(String body) { this.body = body; }
}
