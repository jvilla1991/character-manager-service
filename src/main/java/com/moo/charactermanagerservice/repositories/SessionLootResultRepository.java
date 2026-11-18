package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionLootResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface SessionLootResultRepository extends JpaRepository<SessionLootResult, Long> {

    /** The session's latest split outcome — the unique index (V48) guarantees at most one. */
    Optional<SessionLootResult> findBySessionId(Long sessionId);

    @Modifying
    void deleteBySessionId(Long sessionId);
}
