package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A character the DM has placed at the active {@link SessionShop}. Only these
 * PCs (plus the DM) can see and buy from the shop — this row is what makes shop
 * attendance "session-exclusive." It carries no long-term meaning and is
 * cascade-deleted with the shop or the session. {@code ownerUserId} is
 * denormalized from the PC so visibility checks don't need a pc lookup.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_shop_attendee")
public class SessionShopAttendee implements Serializable {

    public SessionShopAttendee() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionShopId;
    private Long pcId;
    private UUID ownerUserId;

    @Column(name = "added_at")
    private Instant addedAt;

    @PrePersist
    void onCreate() {
        this.addedAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setSessionShopId(Long sessionShopId) { this.sessionShopId = sessionShopId; }
    public void setPcId(Long pcId) { this.pcId = pcId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
}
