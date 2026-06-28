package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PC;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PCRepository extends JpaRepository<PC, Long> {
    List<PC> findByUserId(UUID userId);

    List<PC> findByCampaignId(Long campaignId);

    /**
     * Load a PC under a pessimistic write lock ({@code SELECT ... FOR UPDATE}),
     * for the purchase transaction. Serializes concurrent purchases against the
     * same character so the read-modify-write of coins/inventory can't lose an
     * update. Must be called inside a transaction. Localized to purchasing — the
     * ordinary {@code findById}/{@code save} paths are unaffected.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PC p where p.id = :id")
    Optional<PC> findByIdForUpdate(@Param("id") Long id);
}
