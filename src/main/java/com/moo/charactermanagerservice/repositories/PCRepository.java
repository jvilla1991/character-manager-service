package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PC;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PCRepository extends JpaRepository<PC, Long> {
    List<PC> findByUser(UUID uuid);
}
