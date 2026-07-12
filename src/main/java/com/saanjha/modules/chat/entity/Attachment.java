package com.saanjha.modules.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata only - Chat never owns the binary. {@code storageReference} is an
 * opaque handle a client resolves via the owning {@link StorageProvider}
 * (e.g. a Cloudinary public_id, resolved to a signed URL by the shared
 * CloudinaryService - same one Task/User already use for their own uploads).
 */
@Entity
@Table(name = "cht_attachments", schema = "cht")
@Getter
@Setter
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @Column(nullable = false, length = 128)
    private String checksum;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 30)
    private StorageProvider storageProvider = StorageProvider.CLOUDINARY;

    @Column(name = "storage_reference", nullable = false, length = 1000)
    private String storageReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
