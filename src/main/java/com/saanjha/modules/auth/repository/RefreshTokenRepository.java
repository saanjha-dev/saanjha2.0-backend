package com.saanjha.modules.auth.repository;

import com.saanjha.modules.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * FIX (TD11, architecture-review.md §7 item 2): {@code rotate()} previously
     * read the token via a plain, unlocked {@code findByTokenHash}, then later
     * wrote {@code used = true} — a check-then-act gap wide enough for two
     * near-simultaneous rotation calls against the same token to both pass
     * the reuse check before either commits. This pessimistic write lock
     * closes that window: the second concurrent caller blocks until the
     * first's transaction commits, then re-reads and correctly sees
     * {@code used = true}, triggering reuse detection instead of a race.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForRotation(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.sessionId = :sessionId AND r.revoked = false")
    void revokeAllTokensForSession(@Param("sessionId") UUID sessionId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.sessionId IN (SELECT s.id FROM AuthSession s WHERE s.userId = :userId) AND r.revoked = false")
    void revokeAllTokensForUser(@Param("userId") UUID userId);
}
