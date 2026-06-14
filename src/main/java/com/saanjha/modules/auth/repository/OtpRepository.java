package com.saanjha.modules.auth.repository;

import com.saanjha.modules.auth.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<VerificationCode, UUID> {

    /**
     * Exact match for the PostgreSQL Partial Index (idx_verification_codes_lookup).
     * This query executes in ~1ms even with millions of expired rows in the table.
     */
    Optional<VerificationCode> findByUserIdAndPurposeAndUsedFalseAndExpiresAtAfter(
            UUID userId, VerificationCode.Purpose purpose, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE VerificationCode v SET v.used = true WHERE v.id = :id")
    void markAsUsed(@Param("id") UUID id);

    /**
     * Prevents OTP spam vulnerabilities. When a user requests a new OTP,
     * this destroys all previously pending OTPs for that specific purpose.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE VerificationCode v SET v.used = true WHERE v.userId = :userId AND v.purpose = :purpose AND v.used = false")
    void invalidateAllPendingForUser(@Param("userId") UUID userId, @Param("purpose") VerificationCode.Purpose purpose);
}