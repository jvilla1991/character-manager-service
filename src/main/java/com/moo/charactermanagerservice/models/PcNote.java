package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A per-character session note: a log entry the OWNING PLAYER writes on their
 * character sheet. The campaign DM may read a member's notes (cross-link);
 * only the owner writes. {@code sessionId} optionally records the live
 * session it was taken in; {@code campaignId} snapshots the campaign at write
 * time. Mirrors {@link SessionNote}'s conventions.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "pc_note")
public class PcNote implements Serializable {

    public PcNote() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pcId;

    private Long campaignId;  // nullable — the PC may not be in a campaign

    private Long sessionId;   // nullable — null when written outside a live session

    private UUID authorUserId;

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
    public void setPcId(Long pcId) { this.pcId = pcId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setAuthorUserId(UUID authorUserId) { this.authorUserId = authorUserId; }
    public void setBody(String body) { this.body = body; }
}
