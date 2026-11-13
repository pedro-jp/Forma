package com.forma.backend.repository;

import com.forma.backend.entity.SecurityIncidentLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityIncidentLogRepository extends JpaRepository<SecurityIncidentLogEntity, String> {
    List<SecurityIncidentLogEntity> findTop50ByOrderByDetectedAtUtcDesc();
}
