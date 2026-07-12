package com.saanjha.modules.chat.repository;

import com.saanjha.modules.chat.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByMessageId(UUID messageId);

    List<Attachment> findByMessageIdIn(List<UUID> messageIds);
}
