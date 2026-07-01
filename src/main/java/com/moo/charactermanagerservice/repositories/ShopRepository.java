package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** A campaign's curated shops, newest first. */
    List<Shop> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
