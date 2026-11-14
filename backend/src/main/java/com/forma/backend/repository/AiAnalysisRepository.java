package com.forma.backend.repository;

import com.forma.backend.entity.AiAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiAnalysisRepository extends JpaRepository<AiAnalysisEntity, UUID> {
    List<AiAnalysisEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserId(UUID userId);
    @Query("SELECT a.userId, COUNT(a) FROM AiAnalysisEntity a GROUP BY a.userId")
    List<Object[]> countGroupedByUserId();
    void deleteByUserId(UUID userId);
}
