package com.saanjha.modules.auth.repository;

import com.saanjha.modules.auth.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    Optional<AuthUser> findByEmail(String email);

    boolean existsByEmail(String email);

    // Direct update prevents pulling the entire entity into memory just to flip a boolean
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AuthUser u SET u.emailVerified = true WHERE u.id = :userId")
    void markEmailAsVerified(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AuthUser u SET u.passwordHash = :newPasswordHash WHERE u.id = :userId")
    void updatePassword(@Param("userId") UUID userId, @Param("newPasswordHash") String newPasswordHash);
}