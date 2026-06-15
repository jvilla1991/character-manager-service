package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByDmUserId(UUID dmUserId);

    Optional<Campaign> findByInviteCode(String inviteCode);
}
