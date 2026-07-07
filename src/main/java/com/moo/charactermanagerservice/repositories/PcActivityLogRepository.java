package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.PcActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PcActivityLogRepository extends JpaRepository<PcActivityLog, Long> {

    List<PcActivityLog> findTop10ByPcIdOrderByCreatedAtDescIdDesc(Long pcId);

    /**
     * Delete every row for this character except the latest 10 (by
     * created_at desc, id desc — the same tiebreak the read uses). A plain
     * JPQL delete can't express LIMIT, so this is native SQL. Runs after
     * every insert, so the subquery only ever scans ~10+few rows per PC.
     */
    @Modifying
    @Query(value = """
        DELETE FROM pc_activity_log
        WHERE pc_id = :pcId AND id NOT IN (
            SELECT id FROM pc_activity_log WHERE pc_id = :pcId
            ORDER BY created_at DESC, id DESC LIMIT 10)
        """, nativeQuery = true)
    void pruneToLatest(@Param("pcId") Long pcId);
}
