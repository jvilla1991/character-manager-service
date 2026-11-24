package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PcFollower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PcFollowerRepository extends JpaRepository<PcFollower, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from PcFollower f where f.id = :id")
    java.util.Optional<PcFollower> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    /** A character's followers in the order they were granted. */
    List<PcFollower> findByPcIdOrderByCreatedAtAscIdAsc(Long pcId);

    long countByPcId(Long pcId);

    /** A character's followers of one kind — its familiar, when asked for FAMILIAR. */
    List<PcFollower> findByPcIdAndKind(Long pcId, com.moo.charactermanagerservice.models.FollowerKind kind);

    /** Every follower of the given characters in one query, oldest (lowest id) first. */
    List<PcFollower> findByPcIdInOrderByIdAsc(java.util.Collection<Long> pcIds);
}
