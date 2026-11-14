package com.forma.backend.repository;

import com.forma.backend.entity.WorkoutEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkoutEntryRepository extends JpaRepository<WorkoutEntryEntity, UUID> {
    List<WorkoutEntryEntity> findByUserIdOrderByWorkoutDateDesc(UUID userId);
    long countByUserId(UUID userId);
    @Query("SELECT w.userId, COUNT(w) FROM WorkoutEntryEntity w GROUP BY w.userId")
    List<Object[]> countGroupedByUserId();
    void deleteByUserId(UUID userId);
}
