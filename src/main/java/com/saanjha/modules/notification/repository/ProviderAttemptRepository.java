package com.saanjha.modules.notification.repository;

import com.saanjha.modules.notification.entity.ProviderAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProviderAttemptRepository extends JpaRepository<ProviderAttempt, UUID> {
    List<ProviderAttempt> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);
}
