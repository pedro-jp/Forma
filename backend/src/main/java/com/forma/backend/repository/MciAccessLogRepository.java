package com.forma.backend.repository;

import com.forma.backend.entity.MciAccessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MciAccessLogRepository extends JpaRepository<MciAccessLogEntity, String> {
    List<MciAccessLogEntity> findByAccountRefHashOrderByAccessedAtUtcDesc(String accountRefHash);

    @Modifying
    @Query("DELETE FROM MciAccessLogEntity m WHERE m.retainUntilUtc < :cutoff")
    int deleteExpiredLogs(@Param("cutoff") Instant cutoff);
}
