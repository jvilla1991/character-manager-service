package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CombatSessionRepository extends JpaRepository<CombatSession, Long> {

    /**
     * The campaign's current non-ended session, if any. The partial unique index
     * (see V8) guarantees at most one row matches, so this returns at most one.
     */
    Optional<CombatSession> findByCampaignIdAndStatusNot(Long campaignId, SessionStatus status);

    /**
     * Atomically close an open long rest phase and report whether THIS caller
     * closed it (1) or it was already closed (0). The DM's Finish, the last
     * player's Ready, the lazy check in the poll path and Interrupt can all
     * race; the conditional update lets exactly one of them win, so recovery
     * is never applied twice. Must be called inside a transaction.
     */
    @Modifying
    @Query("update CombatSession s set s.longRestOpen = false "
            + "where s.id = :id and s.longRestOpen = true")
    int closeLongRest(@Param("id") Long id);
}
