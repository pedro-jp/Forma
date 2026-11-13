package com.forma.backend.repository;

import com.forma.backend.entity.LegalConsentLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LegalConsentLedgerRepository extends JpaRepository<LegalConsentLedgerEntity, String> {
    List<LegalConsentLedgerEntity> findByUserIdOrderByRecordedAtUtcDesc(String userId);

    @Modifying
    @Query("UPDATE LegalConsentLedgerEntity l SET l.userId = :anonUserId WHERE l.userId = :userId")
    void pseudonymizeByUserId(@Param("userId") String userId, @Param("anonUserId") String anonUserId);
}
