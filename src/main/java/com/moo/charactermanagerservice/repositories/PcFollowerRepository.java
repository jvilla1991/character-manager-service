package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PcFollower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PcFollowerRepository extends JpaRepository<PcFollower, Long> {
    /** A character's followers in the order they were granted. */
    List<PcFollower> findByPcIdOrderByCreatedAtAscIdAsc(Long pcId);

    long countByPcId(Long pcId);
}
