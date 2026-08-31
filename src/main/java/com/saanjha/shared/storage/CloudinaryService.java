package com.saanjha.shared.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    private final Cloudinary cloudinary;
    
    // Enterprise Guardrail: Only allow actual images, not disguised PDFs or scripts
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp", "image/jpg");

    // Chat file uploads: generous type allowlist covering images, documents, audio, video
    private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
            // Images
            "image/jpeg", "image/png", "image/webp", "image/jpg", "image/gif", "image/svg+xml", "image/bmp",
            // Documents
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv", "application/json", "application/xml",
            "application/zip", "application/x-rar-compressed", "application/gzip",
            // Audio
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm", "audio/aac", "audio/mp4",
            // Video
            "video/mp4", "video/webm", "video/ogg", "video/quicktime", "video/x-msvideo"
    );

    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024; // 100MB

    /**
     * Uploads an image to Cloudinary and returns the secure HTTPS URL.
     */
    public String uploadImage(MultipartFile file, String folderName, UUID identifier) {
        if (file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File is empty.");
        }
        
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid file type. Only JPEG, PNG, and WEBP are allowed.");
        }

        try {
            // Organize files cleanly in Cloudinary folders (e.g., saanjha2/users/uuid)
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", "saanjha2/" + folderName,
                    "public_id", identifier.toString(), // Overwrites the old image automatically
                    "overwrite", true,
                    "resource_type", "image"
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            return uploadResult.get("secure_url").toString();

        } catch (IOException e) {
            log.error("Failed to upload image to Cloudinary", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to upload image. Please try again.");
        }
    }

    /**
     * Uploads any file type (images, documents, audio, video) to Cloudinary.
     * Uses resource_type "auto" so Cloudinary auto-detects the optimal handling.
     * Enforces a 100MB max file size.
     *
     * @return a Map with keys: "secure_url", "public_id", "resource_type", "format", "bytes"
     */
    public Map<String, Object> uploadFile(MultipartFile file, String folderName, UUID identifier) {
        if (file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File is empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "File size exceeds the maximum limit of 100MB. Your file is " +
                    String.format("%.1f", file.getSize() / (1024.0 * 1024.0)) + "MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Unsupported file type: " + (contentType != null ? contentType : "unknown") +
                    ". Allowed: images, documents (PDF, Word, Excel, PPT), audio, and video files.");
        }

        try {
            String uniqueId = identifier.toString() + "_" + System.currentTimeMillis();
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", "saanjha2/" + folderName,
                    "public_id", uniqueId,
                    "overwrite", false,
                    "resource_type", "auto"
            );

            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);

            Object resTypeObj = uploadResult.get("resource_type");
            String resourceType = resTypeObj != null ? resTypeObj.toString() : "raw";
            
            Object formatObj = uploadResult.get("format");
            String format = formatObj != null ? formatObj.toString() : "";
            
            Object bytesObj = uploadResult.get("bytes");
            long bytes = bytesObj != null ? Long.parseLong(bytesObj.toString()) : 0L;

            return Map.of(
                    "secure_url", uploadResult.get("secure_url").toString(),
                    "public_id", uploadResult.get("public_id").toString(),
                    "resource_type", resourceType,
                    "format", format,
                    "bytes", bytes
            );

        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to upload file. Please try again.");
        }
    }
}