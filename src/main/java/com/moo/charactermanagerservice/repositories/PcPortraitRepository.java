package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PcPortrait;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PcPortraitRepository extends JpaRepository<PcPortrait, Long> {
}
