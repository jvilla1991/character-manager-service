package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A single entry in a character's read-only activity log: a pre-rendered
 * display string recorded at a backend mutation point (level-up, shop
 * purchase/sale, XP award, long rest, DM edit). Mirrors {@link PcNote}'s
 * conventions. There is no client-authored content here — every row is
 * written by the server, never by a request body.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "pc_activity_log")
public class PcActivityLog implements Serializable {

    public PcActivityLog() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pcId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    private PcActivityType actionType;

    @Column(columnDefinition = "TEXT")
    private String description;

    private UUID actorUserId;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Setters ---

    public void setId(Long id) { this.id = id; }
    public void setPcId(Long pcId) { this.pcId = pcId; }
    public void setActionType(PcActivityType actionType) { this.actionType = actionType; }
    public void setDescription(String description) { this.description = description; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
}
