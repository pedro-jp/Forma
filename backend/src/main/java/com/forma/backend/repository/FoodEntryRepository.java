package com.forma.backend.repository;

import com.forma.backend.entity.FoodEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FoodEntryRepository extends JpaRepository<FoodEntryEntity, UUID> {
    List<FoodEntryEntity> findByUserIdOrderByConsumedAtDesc(UUID userId);
    long countByUserId(UUID userId);
    @Query("SELECT f.userId, COUNT(f) FROM FoodEntryEntity f GROUP BY f.userId")
    List<Object[]> countGroupedByUserId();
    void deleteByUserId(UUID userId);
}
