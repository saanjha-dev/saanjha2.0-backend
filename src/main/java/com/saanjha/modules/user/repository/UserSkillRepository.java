package com.saanjha.modules.user.repository;

import com.saanjha.modules.user.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {
    
    List<UserSkill> findAllByProfile_UserId(UUID userId);
    
    Optional<UserSkill> findByProfile_UserIdAndSkillNameIgnoreCase(UUID userId, String skillName);
    
    void deleteByIdAndProfile_UserId(UUID skillId, UUID userId);
    
    boolean existsByProfile_UserIdAndSkillNameIgnoreCase(UUID userId, String skillName);
}