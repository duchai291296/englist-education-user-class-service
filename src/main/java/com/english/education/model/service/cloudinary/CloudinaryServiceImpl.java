package com.english.education.model.service.cloudinary;

import com.cloudinary.AuthToken;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.english.education.constant.MessageConstant;
import com.english.education.exception.CustomException;
import com.english.education.model.enums.CloudinaryResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * CloudinaryServiceImpl
 * <p>
 * Responsible for handling image operations with Cloudinary:
 * - Upload authenticated (private) images
 * - Upload public images
 * - Generate signed/private URLs
 * - Delete images
 * <p>
 * This service does NOT participate in database transactions.
 * Cloudinary operations must be manually compensated if DB rollback occurs.
 *
 * @author Duc Hai
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryServiceImpl implements CloudinaryService {

    // Cloudinary API parameter keys
    private static final String PUBLIC_ID = "public_id";
    private static final String RESOURCE_TYPE = "resource_type";
    private static final String IMAGE = "image";
    private static final String OVERWRITE = "overwrite";
    private static final String ACCESS_MODE = "access_mode";
    private static final String INVALIDATE = "invalidate";
    private static final String AUTHENTICATED = "authenticated";
    private static final String PUBLIC = "public";

    // Cloudinary client instance (injected via constructor)
    private final Cloudinary cloudinary;

    /**
     * Upload an image file to Cloudinary with a fixed public_id (prefix).
     * <p>
     * Image is uploaded as AUTHENTICATED resource.
     * Access requires to be signed URL with expiration.
     * <p>
     * Use case:
     * - Private images
     * - Sensitive user documents
     *
     * @param file   the image file
     * @param prefix the desired public_id (e.g., "documents/123")
     * @return actual public_id returned by Cloudinary
     * @throws CustomException if validation or upload fails
     * @author Duc Hai
     */
    @Override
    public String uploadImageWithPrefix(MultipartFile file, String prefix) throws CustomException {
        // Upload image as authenticated (private) resource
        // Requires to be signed URL to access
        return upload(file, prefix, CloudinaryResourceType.IMAGE, AUTHENTICATED);
    }

    /**
     * Upload an image file to Cloudinary as PUBLIC resource.
     * <p>
     * Public images can be accessed directly via HTTPS URL without authentication.
     * Suitable for avatars, thumbnails, public assets.
     *
     * @param file   the image file to upload
     * @param prefix the desired public_id (e.g., "avatar/123")
     * @return the actual public_id returned by Cloudinary
     */
    @Override
    public String uploadPublicImageWithPrefix(MultipartFile file, String prefix) throws CustomException {
        // Upload image as public resource
        // Can be accessed directly via HTTPS URL without authentication
        return upload(file, prefix, CloudinaryResourceType.IMAGE, PUBLIC);
    }

    /**
     * Generate a signed (private) URL for an authenticated image.
     *
     * @param publicId      the Cloudinary public_id
     * @param expireSeconds number of seconds until URL expires
     * @return signed HTTPS URL or null if publicId invalid
     * @author Duc Hai
     */
    @Override
    public String getPrivateUrl(String publicId, int expireSeconds) {
        // Validate input: publicId must not be null or empty
        if (publicId == null || publicId.isBlank()) {
            return null;
        }

        // Calculate expiration timestamp (current time + expireSeconds)
        // Convert milliseconds to seconds (Unix timestamp format)
        long expiration = (System.currentTimeMillis() / 1000) + expireSeconds;

        // Create token options with expiration time
        Map<String, Object> tokenOptions = new HashMap<>();
        tokenOptions.put("expiration", expiration);

        // Generate authentication token for signed URL
        AuthToken authToken = new AuthToken(tokenOptions);

        // Build and generate signed HTTPS URL for authenticated image
        return cloudinary.url()
                .secure(true)                    // Use HTTPS protocol
                .resourceType(IMAGE)             // Specify resource type as image
                .type(AUTHENTICATED)           // Set type to authenticated (private)
                .authToken(authToken)            // Attach authentication token with expiration
                .generate(publicId.trim());      // Generate URL for the given public_id
    }

    /**
     * Generate a public HTTPS URL for a Cloudinary public image.
     * <p>
     * The image must be uploaded with access_mode = "public".
     * Returned URL can be used directly by frontend to display the image.
     *
     * @param publicId Cloudinary public_id (e.g., "avatar/123")
     * @return HTTPS URL or null if publicId is invalid
     */
    @Override
    public String getPublicImageUrl(String publicId) {
        // Validate input: publicId must not be null or empty
        if (publicId == null || publicId.isBlank()) {
            return null;
        }

        // Generate public HTTPS URL (no authentication required)
        return cloudinary.url()
                .secure(true)
                .resourceType(IMAGE)
                .generate(publicId.trim());
    }

    /**
     * Delete an image from Cloudinary.
     * <p>
     * This method should be used as a compensation action
     * when database transactions fail after upload.
     *
     * @param publicId the Cloudinary public_id
     * @author Duc Hai
     */
    @Override
    public void deleteImage(String publicId) {
        try {
            // Delete image from Cloudinary using destroy API
            cloudinary.uploader().destroy(
                    publicId,                    // The public_id of image to delete
                    ObjectUtils.asMap(
                            RESOURCE_TYPE, IMAGE,  // Specify resource type as image
                            INVALIDATE, true       // Invalidate CDN cache after deletion
                    )
            );
            log.info("Cloudinary image deleted: {}", publicId);
        } catch (Exception e) {
            // Log error but don't throw exception
            // This allows deletion to fail silently (useful for compensation actions)
            log.error("Cloudinary delete image failed", e);
        }
    }

    /**
     * Validate uploaded image file.
     * <p>
     * Rules:
     * - File must not be null or empty
     * - Content type must start with "image/"
     * - File size must be <= 10MB
     *
     * @param file multipart file
     * @throws CustomException if validation fails
     * @author Duc Hai
     */
    private void validateFile(MultipartFile file) throws CustomException {
        // Validation 1: Check if file is null or empty
        if (file == null || file.isEmpty()) {
            throw new CustomException(MessageConstant.IMAGE_FILE_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        // Validation 2: Check content type must be an image
        // Valid types: image/jpeg, image/png, image/gif, etc.
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(MessageConstant.IMAGE_INVALID_FORMAT, HttpStatus.BAD_REQUEST);
        }

        // Validation 3: Check file size limit (10MB = 10 * 1024 * 1024 bytes)
        long maxSize = 10L * 1024 * 1024; // 10MB in bytes
        if (file.getSize() > maxSize) {
            throw new CustomException(MessageConstant.IMAGE_FILE_TOO_LARGE, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Core upload method that handles image upload to Cloudinary.
     * This is a private helper method used by both public and authenticated upload methods.
     *
     * @param file         the image file to upload
     * @param publicId     the desired public_id (unique identifier in Cloudinary)
     * @param resourceType type of resource (IMAGE, VIDEO, etc.)
     * @param accessMode   access mode: "public" or "authenticated"
     * @return the actual public_id returned by Cloudinary
     * @throws CustomException if validation or upload fails
     * @author Duc Hai
     */
    private String upload(MultipartFile file, String publicId, CloudinaryResourceType resourceType, String accessMode) throws CustomException {
        try {
            // Step 1: Validate the uploaded file (size, type, etc.)
            validateFile(file);

            // Step 2: Prepare upload options for Cloudinary API
            Map<String, Object> options = new HashMap<>();
            options.put(PUBLIC_ID, publicId.trim());
            options.put(RESOURCE_TYPE, resourceType.value());
            options.put(OVERWRITE, true);
            options.put(ACCESS_MODE, accessMode);

            // Step 3: Upload file to Cloudinary
            // Suppress unchecked warning for Map cast from Cloudinary API
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader()
                    .upload(file.getBytes(), options);             // Convert file to bytes and upload

            // Step 4: Extract public_id from upload result
            String uploadPublicId = (String) uploadResult.get(PUBLIC_ID);
            log.info("Image uploaded successfully: publicId={}, accessMode={}", publicId, accessMode);
            return uploadPublicId;

        } catch (CustomException e) {
            // Re-throw validation exceptions as-is
            throw e;
        } catch (Exception e) {
            // Catch any other exceptions (network errors, Cloudinary API errors, etc.)
            log.error("Cloudinary upload failed: publicId={}, accessMode={}", publicId, accessMode, e);
            throw new CustomException(
                    MessageConstant.IMAGE_UPLOAD_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
