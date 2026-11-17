package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PcProfession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PcProfessionRepository extends JpaRepository<PcProfession, Long> {
    List<PcProfession> findByPcIdOrderByIdAsc(Long pcId);
    Optional<PcProfession> findByPcIdAndProfession(Long pcId, String profession);
}
