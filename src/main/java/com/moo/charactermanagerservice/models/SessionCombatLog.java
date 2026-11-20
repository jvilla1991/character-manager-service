package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One health or condition change recorded during a live session (Session
 * Mode's DM-only "Combat Log" panel). Ephemeral like {@link SessionRoll}:
 * deleted in bulk when the session ends (SessionService#endSession and the
 * lazy TTL-expiry path) — there is no long-term combat history.
 *
 * <p>{@code subjectName} and {@code description} are stored as text, already
 * worded, so a row still reads correctly after the combatant it is about has
 * left the session (a defeated enemy's row is deleted on the spot).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_combat_log")
public class SessionCombatLog implements Serializable {

    public SessionCombatLog() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private CombatLogType eventType;

    private String subjectName;

    @Column(columnDefinition = "TEXT")
    private String description;

    // The encounter round the change happened in; null outside an encounter.
    private Short round;

    private UUID actorUserId;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public void setId(Long id) { this.id = id; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setEventType(CombatLogType eventType) { this.eventType = eventType; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public void setDescription(String description) { this.description = description; }
    public void setRound(Short round) { this.round = round; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
