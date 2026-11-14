package com.forma.backend.repository;

import com.forma.backend.entity.WeightEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WeightEntryRepository extends JpaRepository<WeightEntryEntity, UUID> {
    List<WeightEntryEntity> findByUserIdOrderByEntryDateDesc(UUID userId);
    long countByUserId(UUID userId);
    @Query("SELECT w.userId, COUNT(w) FROM WeightEntryEntity w GROUP BY w.userId")
    List<Object[]> countGroupedByUserId();
    void deleteByUserId(UUID userId);
}
