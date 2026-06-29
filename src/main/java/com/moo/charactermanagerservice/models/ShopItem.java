package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * One line in a {@link Shop}: a catalog item ({@code catalogItemKey} →
 * {@code srd_item.item_key}) with an optional price override. {@code priceCp}
 * null means inherit the catalog price. Phase 2 MVP has no quantity column —
 * curated shops are unlimited until the deferred finite-stock work lands.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "shop_item")
public class ShopItem implements Serializable {

    public ShopItem() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;
    private String catalogItemKey;
    private Long priceCp; // null = inherit catalog price

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public void setCatalogItemKey(String catalogItemKey) { this.catalogItemKey = catalogItemKey; }
    public void setPriceCp(Long priceCp) { this.priceCp = priceCp; }
}
