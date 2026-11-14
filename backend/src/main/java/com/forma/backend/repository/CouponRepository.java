package com.forma.backend.repository;

import com.forma.backend.entity.CouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<CouponEntity, UUID> {
    Optional<CouponEntity> findByCodeIgnoreCase(String code);
    List<CouponEntity> findAllByOrderByCreatedAtUtcDesc();
}
