package com.saanjha.modules.auth.repository;

import com.saanjha.modules.auth.entity.AuthTrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthTrustedDeviceRepository extends JpaRepository<AuthTrustedDevice, UUID> {
    Optional<AuthTrustedDevice> findByUserIdAndDeviceId(UUID userId, String deviceId);
    void deleteByUserId(UUID userId);
}
