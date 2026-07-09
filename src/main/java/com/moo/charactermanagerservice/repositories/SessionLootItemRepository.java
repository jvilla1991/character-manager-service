package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionLootItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionLootItemRepository extends JpaRepository<SessionLootItem, Long> {

    List<SessionLootItem> findBySessionLootIdOrderByIdAsc(Long sessionLootId);

    void deleteBySessionLootId(Long sessionLootId);

    /**
     * Load a loot line under a pessimistic write lock for an item claim —
     * serializes concurrent claims on the same line so the last one can't be
     * taken twice. Must be called inside a transaction; lock the loot row
     * BEFORE the pc row (fixed order shared with coin claims prevents deadlock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SessionLootItem i where i.id = :id")
    Optional<SessionLootItem> findByIdForUpdate(@Param("id") Long id);
}
