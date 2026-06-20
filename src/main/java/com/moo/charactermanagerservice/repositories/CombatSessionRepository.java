package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CombatSessionRepository extends JpaRepository<CombatSession, Long> {

    /**
     * The campaign's current non-ended session, if any. The partial unique index
     * (see V8) guarantees at most one row matches, so this returns at most one.
     */
    Optional<CombatSession> findByCampaignIdAndStatusNot(Long campaignId, SessionStatus status);
}
