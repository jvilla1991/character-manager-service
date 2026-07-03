package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A live encounter for a campaign. Owned by {@code dmUserId} (the auth-service
 * user UUID); its combatants are {@link SessionParticipant} rows. At most one
 * non-ended session exists per campaign (enforced by a partial unique index in
 * {@code V8__session_mode.sql}). Column names map from camelCase via the default
 * snake_case naming strategy, matching {@link Campaign} and {@link PC}.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "combat_session")
public class CombatSession implements Serializable {

    public CombatSession() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long campaignId;

    private UUID dmUserId;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.LOBBY;

    private Short round = 1;

    // Stable turn pointer: the participant whose turn it is. Never moves when the
    // order re-sorts (it's an ID, not a position). Null until the encounter starts;
    // left in place on END so history records where combat stopped.
    private Long currentTurnParticipantId;

    // Monotonic counter bumped on every mutation so pollers can skip unchanged state.
    private Long version = 0L;

    // DM checkbox: when true (default), players' snapshots omit enemy combatants
    // entirely — glow/on-deck are computed per viewer against what they can see.
    private Boolean enemiesHidden = true;

    // Encounter-level turn-cue key chosen by the DM; clients play it on turn
    // change (each device can mute locally). Null = no sound.
    private String turnSound;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Increment the version; call on any state-changing operation. */
    public void bumpVersion() {
        this.version = (this.version == null ? 0L : this.version) + 1;
    }

    // --- Setters ---

    public void setId(Long id) { this.id = id; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public void setDmUserId(UUID dmUserId) { this.dmUserId = dmUserId; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public void setRound(Short round) { this.round = round; }
    public void setCurrentTurnParticipantId(Long currentTurnParticipantId) { this.currentTurnParticipantId = currentTurnParticipantId; }
    public void setVersion(Long version) { this.version = version; }
    public void setEnemiesHidden(Boolean enemiesHidden) { this.enemiesHidden = enemiesHidden; }
    public void setTurnSound(String turnSound) { this.turnSound = turnSound; }
}
