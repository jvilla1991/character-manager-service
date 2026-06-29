package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.ShopItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {

    /** A shop's lines. */
    List<ShopItem> findByShopId(Long shopId);

    /** A shop's line for a given catalog item, if present (dedupe / lookup). */
    Optional<ShopItem> findByShopIdAndCatalogItemKey(Long shopId, String catalogItemKey);
}
