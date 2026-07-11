package com.saanjha.modules.notification.repository;

import com.saanjha.modules.notification.entity.ProviderHealth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderHealthRepository extends JpaRepository<ProviderHealth, String> {
    List<ProviderHealth> findAllByOrderByChannelAscConsecutiveFailuresAsc();
}
