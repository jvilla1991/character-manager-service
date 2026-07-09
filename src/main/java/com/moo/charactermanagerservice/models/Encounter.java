package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A DM-curated encounter: a persistent, reusable encounter definition (a free-hand
 * list of enemy creatures). Owned by the campaign's DM ({@code dmUserId}); DM-only
 * actions assert that ownership, mirroring {@link Shop}. Loaded into a live session
 * by {@code SessionService.loadEncounter}, which turns each {@link EncounterCreature}
 * into an enemy combatant. Its lines are {@link EncounterCreature}s.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "encounter")
public class Encounter implements Serializable {

    public Encounter() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long campaignId;
    private UUID dmUserId;
    private String name;
    private String notes;

    /** The prepped coin pile in copper (V31); its item lines are {@link EncounterLootItem}s. */
    private long lootCoinCp;

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
    public void setLootCoinCp(long lootCoinCp) { this.lootCoinCp = lootCoinCp; }
}
