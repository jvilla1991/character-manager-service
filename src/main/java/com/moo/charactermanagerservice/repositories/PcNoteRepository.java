package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PcNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PcNoteRepository extends JpaRepository<PcNote, Long> {
    List<PcNote> findByPcIdOrderByCreatedAtDesc(Long pcId);
}
