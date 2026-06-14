package com.saanjha.modules.auth.repository;

import com.saanjha.modules.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    List<AuthSession> findAllByUserIdAndActiveTrue(UUID userId);

    Optional<AuthSession> findByIdAndActiveTrue(UUID id);

    // O(1) specific session termination
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AuthSession s SET s.active = false WHERE s.id = :sessionId")
    void deactivateSession(@Param("sessionId") UUID sessionId);

    // Bulk "Logout Everywhere" functionality
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AuthSession s SET s.active = false WHERE s.userId = :userId AND s.active = true")
    void deactivateAllUserSessions(@Param("userId") UUID userId);

    // Optimized activity heartbeat update
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AuthSession s SET s.lastActivityAt = :now WHERE s.id = :sessionId")
    void updateLastActivity(@Param("sessionId") UUID sessionId, @Param("now") Instant now);
}