package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A DM-curated loot list: a persistent, reusable set of prepped spoils (item
 * lines plus a coin pile) a DM builds on the campaign screen and later drops
 * into a live session's claim pool. Owned by the campaign's DM ({@code dmUserId});
 * DM-only actions assert that ownership, mirroring {@link Shop} and
 * {@link Encounter}. Dropping COPIES the lines into a {@link SessionLoot} pool —
 * the curated list is never mutated, so it survives being dropped any number of
 * times. Its lines are {@link CuratedLootItem}s.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "curated_loot")
public class CuratedLoot implements Serializable {

    public CuratedLoot() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long campaignId;
    private UUID dmUserId;
    private String name;
    private String notes;

    /** The prepped coin pile in copper (1 gp = 100 cp). */
    private long coinCp;

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

    public void setId(Long id) { this.id = id; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public void setDmUserId(UUID dmUserId) { this.dmUserId = dmUserId; }
    public void setName(String name) { this.name = name; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCoinCp(long coinCp) { this.coinCp = coinCp; }
}
