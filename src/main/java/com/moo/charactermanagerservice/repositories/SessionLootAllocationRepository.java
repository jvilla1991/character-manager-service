package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionLootAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface SessionLootAllocationRepository extends JpaRepository<SessionLootAllocation, Long> {

    List<SessionLootAllocation> findBySessionLootIdOrderByIdAsc(Long sessionLootId);

    @Modifying
    void deleteBySessionLootId(Long sessionLootId);

    @Modifying
    void deleteBySessionLootIdAndLootItemId(Long sessionLootId, Long lootItemId);

    /** Coin shares are the rows with no loot line. */
    @Modifying
    void deleteBySessionLootIdAndLootItemIdIsNull(Long sessionLootId);
}
