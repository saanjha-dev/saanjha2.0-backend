package com.saanjha.modules.user.repository;

import com.saanjha.modules.user.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
    
    Optional<UserPreferences> findByProfile_UserId(UUID userId);
}