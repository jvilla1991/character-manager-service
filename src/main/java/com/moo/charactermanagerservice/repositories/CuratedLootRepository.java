package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.CuratedLoot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuratedLootRepository extends JpaRepository<CuratedLoot, Long> {

    List<CuratedLoot> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
