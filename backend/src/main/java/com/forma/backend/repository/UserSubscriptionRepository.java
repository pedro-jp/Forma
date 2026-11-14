package com.forma.backend.repository;

import com.forma.backend.entity.UserSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscriptionEntity, UUID> {
    Optional<UserSubscriptionEntity> findByUserId(UUID userId);
    Optional<UserSubscriptionEntity> findByExternalTransactionId(String externalTransactionId);
    List<UserSubscriptionEntity> findAllByOrderByUpdatedAtUtcDesc();
    List<UserSubscriptionEntity> findByStatusIn(Collection<String> statuses);
    void deleteByUserId(UUID userId);
}
