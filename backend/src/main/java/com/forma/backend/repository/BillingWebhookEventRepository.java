package com.forma.backend.repository;

import com.forma.backend.entity.BillingWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEventEntity, UUID> {
    boolean existsByEventId(String eventId);
    List<BillingWebhookEventEntity> findTop100ByOrderByReceivedAtUtcDesc();
}
