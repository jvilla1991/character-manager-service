package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One dice roll logged during a live session (Session Mode "Roll Log" panel).
 * Ephemeral: deleted in bulk when the session ends (SessionService#endSession
 * and the lazy TTL-expiry path) — there is no long-term roll history. The
 * server trusts the client's already-computed results (breakdown + total);
 * this is a shared table-side log, not a fairness-enforcing RNG.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_roll")
public class SessionRoll implements Serializable {

    public SessionRoll() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Long participantId; // nullable — SET NULL if the participant later leaves
    private UUID ownerUserId;
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String breakdown; // JSON: [{"sides":6,"rolls":[4,2,6]}, ...]

    private Integer grandTotal;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public void setId(Long id) { this.id = id; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setParticipantId(Long participantId) { this.participantId = participantId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setBreakdown(String breakdown) { this.breakdown = breakdown; }
    public void setGrandTotal(Integer grandTotal) { this.grandTotal = grandTotal; }
}
