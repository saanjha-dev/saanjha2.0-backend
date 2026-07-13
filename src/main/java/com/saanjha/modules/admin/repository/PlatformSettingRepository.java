package com.saanjha.modules.admin.repository;

import com.saanjha.modules.admin.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, UUID> {

    Optional<PlatformSetting> findBySettingKey(String settingKey);

    List<PlatformSetting> findAllByOrderBySettingKeyAsc();
}
