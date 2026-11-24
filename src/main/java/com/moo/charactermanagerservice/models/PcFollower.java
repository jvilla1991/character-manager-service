package com.moo.charactermanagerservice.models;

import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A character's follower (V43): a named companion with its own stat block.
 * {@code statBlock} is an opaque JSON object in the client's sheet shape —
 * stored and returned verbatim, never interpreted here. {@code kind} (V64)
 * says what it is and so who creates and dismisses it (see
 * {@link FollowerKind}); the owning player and the DM may both update it.
 * Mirrors {@link PcNote}'s conventions.
 */
@Getter
@Entity
@Table(name = "pc_follower")
public class PcFollower implements Serializable {

    public PcFollower() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pcId;

    private String name;

    @Column(name = "stat_block", columnDefinition = "TEXT")
    private String statBlock;

    private UUID grantedByUserId;

    // Set when the follower is created and never changed afterwards (V64).
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", columnDefinition = "TEXT")
    private FollowerKind kind = FollowerKind.DM_GRANT;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Setters ---

    public void setId(Long id) { this.id = id; }
    public void setPcId(Long pcId) { this.pcId = pcId; }
    public void setName(String name) { this.name = name; }
    public void setStatBlock(String statBlock) { this.statBlock = statBlock; }
    public void setGrantedByUserId(UUID grantedByUserId) { this.grantedByUserId = grantedByUserId; }
    public void setKind(FollowerKind kind) { this.kind = kind; }
}
