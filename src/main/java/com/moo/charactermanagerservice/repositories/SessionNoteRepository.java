package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionNoteRepository extends JpaRepository<SessionNote, Long> {

    /** A campaign's notes, newest first (the order the DM reads them in). */
    List<SessionNote> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
