package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * A player-initiated short-rest vote — one may be ACTIVE per session (enforced
 * by a partial unique index). Passing unanimously opens the session's
 * {@code shortRestOpen} window. Ephemeral like {@link SessionRoll}: the latest
 * row (terminal included) rides the poll snapshot so clients can show the
 * outcome; rows are purged when the session ends.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_rest_vote")
public class SessionRestVote implements Serializable {

    public SessionRestVote() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Long initiatorParticipantId; // nullable — SET NULL if the initiator leaves
    private String initiatorName;

    @Enumerated(EnumType.STRING)
    private RestVoteStatus status = RestVoteStatus.ACTIVE;

    // Which rest the party is voting on (V52). SHORT for every pre-V52 row.
    @Enumerated(EnumType.STRING)
    private RestType restType = RestType.SHORT;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public void setId(Long id) { this.id = id; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setInitiatorParticipantId(Long initiatorParticipantId) { this.initiatorParticipantId = initiatorParticipantId; }
    public void setInitiatorName(String initiatorName) { this.initiatorName = initiatorName; }
    public void setStatus(RestVoteStatus status) { this.status = status; }
    public void setRestType(RestType restType) { this.restType = restType; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
