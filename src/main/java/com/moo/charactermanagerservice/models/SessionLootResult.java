package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * The latest loot-split outcome in a session (at most one row per session).
 * It outlives the pool it describes — an approved pool is deleted and a
 * cancelled one goes back to a hidden draft — so it rides the poll snapshot
 * for clients to announce. Replaced by the next outcome, purged at session end.
 * {@code outcome} is one of APPROVED, PVP, NO_MAJORITY, DM_APPROVED,
 * DM_CANCELLED.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_loot_result")
public class SessionLootResult implements Serializable {

    public SessionLootResult() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private String lootName;
    private String outcome;
    private String pvpCallers; // comma-separated display names
    private int agreeCount;
    private int objectCount;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public void setId(Long id) { this.id = id; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setLootName(String lootName) { this.lootName = lootName; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public void setPvpCallers(String pvpCallers) { this.pvpCallers = pvpCallers; }
    public void setAgreeCount(int agreeCount) { this.agreeCount = agreeCount; }
    public void setObjectCount(int objectCount) { this.objectCount = objectCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
