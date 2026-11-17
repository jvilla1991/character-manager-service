package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PcRenown;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PcRenownRepository extends JpaRepository<PcRenown, Long> {
    List<PcRenown> findByPcId(Long pcId);
    Optional<PcRenown> findByPcIdAndFactionId(Long pcId, Long factionId);
}
