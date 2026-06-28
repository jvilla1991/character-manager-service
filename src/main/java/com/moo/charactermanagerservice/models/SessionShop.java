package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * The single shop a DM has activated in a live {@link CombatSession}. Transient,
 * session-scoped state: there is at most one per session (unique index in
 * {@code V10}) and it is cascade-deleted when the shop is closed or the session
 * ends. Mirrors {@link SessionParticipant}'s relationship to the session.
 *
 * <p>{@code shopId} is a forward hook for Phase 2 DM-curated shops; while NULL,
 * this is a standard shop whose stock is derived from {@code srd_item} filtered
 * by {@code category}, at catalog price, with unlimited quantity.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_shop")
public class SessionShop implements Serializable {

    public SessionShop() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private String category;
    private String settlement;
    private Long shopId;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public void setCategory(String category) { this.category = category; }
    public void setSettlement(String settlement) { this.settlement = settlement; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
}
