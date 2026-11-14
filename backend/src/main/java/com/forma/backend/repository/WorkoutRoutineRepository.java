package com.forma.backend.repository;

import com.forma.backend.entity.WorkoutRoutineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkoutRoutineRepository extends JpaRepository<WorkoutRoutineEntity, UUID> {
    List<WorkoutRoutineEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserId(UUID userId);
    @Query("SELECT r.userId, COUNT(r) FROM WorkoutRoutineEntity r GROUP BY r.userId")
    List<Object[]> countGroupedByUserId();
    void deleteByUserId(UUID userId);
}
