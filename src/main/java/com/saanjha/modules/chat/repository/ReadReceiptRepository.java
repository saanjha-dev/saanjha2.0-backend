package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.ReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReadReceiptRepository extends JpaRepository<ReadReceipt, UUID> {

    List<ReadReceipt> findByMessageId(UUID messageId);

    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);
}
