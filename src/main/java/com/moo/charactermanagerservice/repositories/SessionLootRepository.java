package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionLoot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionLootRepository extends JpaRepository<SessionLoot, Long> {

    /** The session's loot pool, if any. The unique index (V31) guarantees ≤1. */
    Optional<SessionLoot> findBySessionId(Long sessionId);

    /**
     * Load the pool under a pessimistic write lock for a coin claim, so two
     * players grabbing from the pile can't both see the same remainder. Must be
     * called inside a transaction; lock the loot row BEFORE the pc row (fixed
     * order shared with item claims prevents deadlock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SessionLoot l where l.id = :id")
    Optional<SessionLoot> findByIdForUpdate(@Param("id") Long id);
}
