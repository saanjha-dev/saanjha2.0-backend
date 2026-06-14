package com.saanjha.modules.auth.repository;

import com.saanjha.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Instantly kills the entire token family tree for a compromised session
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.sessionId = :sessionId AND r.revoked = false")
    void revokeAllTokensForSession(@Param("sessionId") UUID sessionId);

    // Sub-query logic to destroy all cryptographic keys globally for a user
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.sessionId IN (SELECT s.id FROM AuthSession s WHERE s.userId = :userId) AND r.revoked = false")
    void revokeAllTokensForUser(@Param("userId") UUID userId);
}