package com.forma.backend.repository;

import com.forma.backend.entity.CouponEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<CouponEntity, UUID> {
    Optional<CouponEntity> findByCodeIgnoreCase(String code);
    List<CouponEntity> findAllByOrderByCreatedAtUtcDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponEntity c WHERE UPPER(c.code) = UPPER(:code)")
    Optional<CouponEntity> findByCodeForUpdate(@Param("code") String code);
}
