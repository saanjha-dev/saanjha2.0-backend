package com.saanjha.modules.user.repository;

import com.saanjha.modules.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    
    Optional<UserProfile> findByUserId(UUID userId);
    
    boolean existsByUserId(UUID userId);
    java.util.Optional<UserProfile> findByUniqueHandleIgnoreCase(String uniqueHandle);

    boolean existsByUniqueHandleIgnoreCaseAndUserIdNot(String uniqueHandle, UUID userId);
}