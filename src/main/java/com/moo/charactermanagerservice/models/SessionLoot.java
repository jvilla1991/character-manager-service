package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * The single loot pool a DM has opened in a live {@link CombatSession}.
 * Transient, session-scoped state like {@link SessionShop}: at most one per
 * session (unique index in V31), deleted when the DM discards it or the session
 * ends — unclaimed loot does not survive. {@code dropped} is the visibility
 * flip: false = DM-only draft the DM is still editing, true = players may see
 * and claim. The coin pile is a single copper amount; players take arbitrary
 * amounts from {@code coinCpRemaining} (no auto-split).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_loot")
public class SessionLoot implements Serializable {

    public SessionLoot() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private String name;
    private boolean dropped;
    private long coinCpTotal;
    private long coinCpRemaining;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setName(String name) { this.name = name; }
    public void setDropped(boolean dropped) { this.dropped = dropped; }
    public void setCoinCpTotal(long coinCpTotal) { this.coinCpTotal = coinCpTotal; }
    public void setCoinCpRemaining(long coinCpRemaining) { this.coinCpRemaining = coinCpRemaining; }
}
