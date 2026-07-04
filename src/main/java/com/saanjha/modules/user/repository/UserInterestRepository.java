package com.saanjha.modules.user.repository;

import com.saanjha.modules.user.entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserInterestRepository extends JpaRepository<UserInterest, UUID> {
    
    List<UserInterest> findAllByProfile_UserId(UUID userId);
    
    Optional<UserInterest> findByProfile_UserIdAndInterestNameIgnoreCase(UUID userId, String interestName);
    
    void deleteByIdAndProfile_UserId(UUID interestId, UUID userId);
    
    boolean existsByProfile_UserIdAndInterestNameIgnoreCase(UUID userId, String interestName);
}