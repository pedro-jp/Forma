package com.forma.backend.repository;

import com.forma.backend.entity.PaymentEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentEntryRepository extends JpaRepository<PaymentEntryEntity, UUID> {
    List<PaymentEntryEntity> findTop200ByOrderByCreatedAtUtcDesc();
    List<PaymentEntryEntity> findByUserIdOrderByCreatedAtUtcDesc(UUID userId);
    Optional<PaymentEntryEntity> findFirstByReferenceCode(String referenceCode);
    Optional<PaymentEntryEntity> findFirstBySubscriptionIdAndStatusOrderByCreatedAtUtcDesc(UUID subscriptionId, String status);
    boolean existsByReferenceCode(String referenceCode);
    void deleteByUserId(UUID userId);
}
