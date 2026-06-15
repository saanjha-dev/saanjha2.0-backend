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
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp", "image/jpg");

    /**
     * Uploads an image to Cloudinary and returns the secure HTTPS URL.
     */
    public String uploadImage(MultipartFile file, String folderName, UUID identifier) {
        if (file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "File is empty.");
        }
        
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
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
}