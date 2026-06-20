package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One combatant in a {@link CombatSession}. A participant is either a player
 * character ({@code pcId} set, {@code ownerUserId} = the PC's owner) or an
 * ad-hoc NPC ({@code pcId} null — NPC stats live on the {@code npc*} columns;
 * wired up in a later phase).
 *
 * <p>For a PC, HP and conditions are NOT stored here — they stay canonical on
 * the {@code pc} row and are read/written there. This row only holds
 * session-scoped state: initiative, ordering, and death-save counters.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_participant")
public class SessionParticipant implements Serializable {

    public SessionParticipant() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;

    // Null for an ad-hoc NPC; otherwise the canonical PC this combatant represents.
    private Long pcId;

    // The PC owner (for player self-edit authorization); null for a DM-added NPC.
    private UUID ownerUserId;

    private String displayName;

    private Short initiative;

    private Boolean initRolled = false;

    private Short orderIndex = 0;

    // --- NPC-only combat state (null for PC rows; PC HP/conditions live on pc) ---
    private Short npcHpCurrent;
    private Short npcHpMax;
    private Short npcHpTemp;

    @Column(columnDefinition = "TEXT")
    private String npcConditions;

    // --- Death saves: session-scoped; reset/discarded at session end (later phase) ---
    private Short deathSaveSuccesses = 0;
    private Short deathSaveFailures = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Setters ---

    public void setId(Long id) { this.id = id; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setPcId(Long pcId) { this.pcId = pcId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setInitiative(Short initiative) { this.initiative = initiative; }
    public void setInitRolled(Boolean initRolled) { this.initRolled = initRolled; }
    public void setOrderIndex(Short orderIndex) { this.orderIndex = orderIndex; }
    public void setNpcHpCurrent(Short npcHpCurrent) { this.npcHpCurrent = npcHpCurrent; }
    public void setNpcHpMax(Short npcHpMax) { this.npcHpMax = npcHpMax; }
    public void setNpcHpTemp(Short npcHpTemp) { this.npcHpTemp = npcHpTemp; }
    public void setNpcConditions(String npcConditions) { this.npcConditions = npcConditions; }
    public void setDeathSaveSuccesses(Short deathSaveSuccesses) { this.deathSaveSuccesses = deathSaveSuccesses; }
    public void setDeathSaveFailures(Short deathSaveFailures) { this.deathSaveFailures = deathSaveFailures; }
}
