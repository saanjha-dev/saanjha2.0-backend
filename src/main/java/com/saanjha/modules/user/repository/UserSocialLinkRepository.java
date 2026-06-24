package com.saanjha.modules.user.repository;

import com.saanjha.modules.user.entity.UserSocialLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSocialLinkRepository extends JpaRepository<UserSocialLink, UUID> {

    List<UserSocialLink> findAllByProfile_UserId(UUID userId);

    Optional<UserSocialLink> findByProfile_UserIdAndPlatformNameIgnoreCase(UUID userId, String platformName);

    void deleteByIdAndProfile_UserId(UUID linkId, UUID userId);
    
    boolean existsByProfile_UserIdAndPlatformNameIgnoreCase(UUID userId, String platformName);
}