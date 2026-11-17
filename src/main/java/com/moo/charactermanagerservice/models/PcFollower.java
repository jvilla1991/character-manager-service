package com.moo.charactermanagerservice.models;

import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A follower the campaign's DM granted to a character (V43): a named companion
 * with its own stat block. {@code statBlock} is an opaque JSON object in the
 * client's sheet shape — stored and returned verbatim, never interpreted here.
 * Only the DM grants or dismisses; the owning player and the DM may update it.
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
}
