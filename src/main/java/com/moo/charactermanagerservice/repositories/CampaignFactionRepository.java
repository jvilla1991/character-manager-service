package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.CampaignFaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignFactionRepository extends JpaRepository<CampaignFaction, Long> {
    List<CampaignFaction> findByCampaignIdOrderByIdAsc(Long campaignId);
}
