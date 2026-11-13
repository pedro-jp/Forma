package com.forma.backend.repository;

import com.forma.backend.entity.AdminAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, String> {
    List<AdminAuditLogEntity> findTop100ByOrderByCreatedAtUtcDesc();
}
