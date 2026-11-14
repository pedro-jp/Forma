package com.forma.backend.repository;

import com.forma.backend.entity.AiRequestLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
    void deleteByUserId(UUID userId);
}
