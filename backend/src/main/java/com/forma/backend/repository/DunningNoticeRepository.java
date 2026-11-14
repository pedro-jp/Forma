package com.forma.backend.repository;

import com.forma.backend.entity.DunningNoticeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DunningNoticeRepository extends JpaRepository<DunningNoticeEntity, UUID> {
    List<DunningNoticeEntity> findTop100ByOrderByCreatedAtUtcDesc();
    List<DunningNoticeEntity> findTop10ByUserIdOrderByCreatedAtUtcDesc(UUID userId);
    void deleteByUserId(UUID userId);
}
