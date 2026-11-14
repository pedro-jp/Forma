package com.forma.backend.repository;

import com.forma.backend.entity.AiRequestLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiRequestLogRepository extends JpaRepository<AiRequestLogEntity, UUID> {
    List<AiRequestLogEntity> findTop50ByOrderByCreatedAtDesc();
    List<AiRequestLogEntity> findTop15ByUserIdOrderByCreatedAtDesc(UUID userId);
    List<AiRequestLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByStatus(String status);
    long countByUserId(UUID userId);
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
    @Query("SELECT l.userId, COUNT(l) FROM AiRequestLogEntity l WHERE l.userId IS NOT NULL GROUP BY l.userId")
    List<Object[]> countTotalGroupedByUserId();
    @Query("SELECT l.userId, COUNT(l) FROM AiRequestLogEntity l WHERE l.userId IS NOT NULL AND l.createdAt > :after GROUP BY l.userId")
    List<Object[]> countSinceGroupedByUserId(@Param("after") Instant after);
    void deleteByUserId(UUID userId);
}
