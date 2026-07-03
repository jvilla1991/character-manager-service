package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    /** A campaign's curated encounters, newest first. */
    List<Encounter> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
