package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PCRepository extends JpaRepository<PC, Long> {
    List<PC> findByUser(User user);
}
