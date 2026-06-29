package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A DM-curated shop: a persistent, reusable shop definition (a chosen subset of
 * the SRD catalog with optional price overrides). Owned by the campaign's DM
 * ({@code dmUserId}); DM-only actions assert that ownership, mirroring
 * {@link CombatSession}. Activated into a live session by pointing
 * {@link SessionShop#getShopId()} at this row. Its lines are {@link ShopItem}s.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "shop")
public class Shop implements Serializable {

    public Shop() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long campaignId;
    private UUID dmUserId;
    private String name;
    private String settlement;

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
    public void setSettlement(String settlement) { this.settlement = settlement; }
}
