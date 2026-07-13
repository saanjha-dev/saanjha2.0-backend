package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByFlagKey(String flagKey);

    List<FeatureFlag> findAllByOrderByFlagKeyAsc();

    boolean existsByFlagKey(String flagKey);
}
