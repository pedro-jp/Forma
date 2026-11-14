package com.forma.backend.repository;

import com.forma.backend.entity.CouponRedemptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemptionEntity, UUID> {
    long countByCouponIdAndUserId(UUID couponId, UUID userId);
    List<CouponRedemptionEntity> findTop100ByOrderByRedeemedAtUtcDesc();
    List<CouponRedemptionEntity> findByUserIdOrderByRedeemedAtUtcDesc(UUID userId);
    void deleteByUserId(UUID userId);
}
