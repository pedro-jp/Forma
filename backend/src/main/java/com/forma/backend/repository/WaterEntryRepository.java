package com.forma.backend.repository;

import com.forma.backend.entity.WaterEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WaterEntryRepository extends JpaRepository<WaterEntryEntity, UUID> {
    List<WaterEntryEntity> findByUserIdOrderByEntryDateDesc(UUID userId);
    long countByUserId(UUID userId);
    @Query("SELECT w.userId, COUNT(w) FROM WaterEntryEntity w GROUP BY w.userId")
    List<Object[]> countGroupedByUserId();
    void deleteByUserId(UUID userId);
}
