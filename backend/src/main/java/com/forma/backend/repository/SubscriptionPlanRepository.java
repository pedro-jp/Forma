package com.forma.backend.repository;

import com.forma.backend.entity.SubscriptionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, String> {
    List<SubscriptionPlanEntity> findByActiveTrueOrderByPriceCentsAsc();
    List<SubscriptionPlanEntity> findAllByOrderByPriceCentsAsc();
    Optional<SubscriptionPlanEntity> findByStorekitProductId(String storekitProductId);
}
