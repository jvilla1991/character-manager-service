package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionLootBallot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface SessionLootBallotRepository extends JpaRepository<SessionLootBallot, Long> {

    List<SessionLootBallot> findBySessionLootIdOrderByIdAsc(Long sessionLootId);

    @Modifying
    void deleteBySessionLootId(Long sessionLootId);
}
