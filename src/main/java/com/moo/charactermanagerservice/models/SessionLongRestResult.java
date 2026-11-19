package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * The latest long rest outcome in a session (at most one row per session). It
 * rides the poll snapshot so every client can announce it once, exactly like
 * {@link SessionLootResult}. Replaced by the next outcome, purged at session
 * end. {@code outcome} is FINISHED or INTERRUPTED; {@code skippedNames} is the
 * newline-separated list of heroes who were at 0 hit points and so got nothing.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_long_rest_result")
public class SessionLongRestResult implements Serializable {

    public SessionLongRestResult() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private String outcome;
    private String skippedNames;
    private boolean clockAdvanced;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public void setId(Long id) { this.id = id; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public void setSkippedNames(String skippedNames) { this.skippedNames = skippedNames; }
    public void setClockAdvanced(boolean clockAdvanced) { this.clockAdvanced = clockAdvanced; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
