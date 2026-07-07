package com.saanjha.modules.task.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata only — Task never owns file storage. {@code storageUrl} points at
 * wherever the actual bytes live (Cloudinary today, per the existing
 * `shared/storage/CloudinaryService`, consistent with how the User module
 * handles profile photos). {@code virusScanStatus} defaults to SKIPPED, the
 * same honest placeholder already used for the User module's profile-photo
 * uploads — see technical-debt.md TD9: no scanning infrastructure exists yet
 * for ANY upload surface in this codebase, and Task inherits that gap rather
 * than silently claiming to have solved it.
 */
@Entity
@Table(name = "tsk_attachments", schema = "tsk")
public class TaskAttachment {

    public enum VirusScanStatus {
        PENDING, CLEAN, INFECTED, SKIPPED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "storage_url", nullable = false, length = 1000)
    private String storageUrl;

    @Column(nullable = false, length = 128)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "virus_scan_status", nullable = false, length = 20)
    private VirusScanStatus virusScanStatus = VirusScanStatus.SKIPPED;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    protected TaskAttachment() {
    }

    public TaskAttachment(UUID taskId, String fileName, long sizeBytes, String contentType, String storageUrl, String checksum, UUID uploadedBy) {
        this.taskId = taskId;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.storageUrl = storageUrl;
        this.checksum = checksum;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public String getChecksum() {
        return checksum;
    }

    public VirusScanStatus getVirusScanStatus() {
        return virusScanStatus;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
