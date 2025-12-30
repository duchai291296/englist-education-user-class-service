package com.english.education.model.service.cloudinary;

import com.cloudinary.AuthToken;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.english.education.constant.MessageConstant;
import com.english.education.exception.CustomException;
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

    private static final String PUBLIC_ID = "public_id";
    private static final String RESOURCE_TYPE = "resource_type";
    private static final String IMAGE = "image";

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
        try {
            validateFile(file);

            Map<String, Object> options = new HashMap<>();
            options.put(PUBLIC_ID, prefix.trim());
            options.put(RESOURCE_TYPE, IMAGE);
            options.put("overwrite", true);
            options.put("access_mode", "authenticated");
            options.put("allowed_formats", new String[]{"jpg", "jpeg", "png", "webp"});

            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader()
                    .upload(file.getBytes(), options);

            String publicId = (String) uploadResult.get(PUBLIC_ID);
            log.info("Image uploaded successfully: publicId={}", publicId);
            return publicId;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary upload failed", e);
            throw new CustomException(
                    MessageConstant.IMAGE_UPLOAD_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
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
        try {
            validateFile(file);

            Map<String, Object> options = new HashMap<>();
            options.put(PUBLIC_ID, prefix.trim());
            options.put(RESOURCE_TYPE, IMAGE);
            options.put("overwrite", true);
            options.put("access_mode", "public");
            options.put("allowed_formats", new String[]{"jpg", "jpeg", "png", "webp"});

            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader()
                    .upload(file.getBytes(), options);

            String publicId = (String) uploadResult.get(PUBLIC_ID);
            log.info("Public image uploaded successfully: publicId={}", publicId);
            return publicId;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary public upload failed", e);
            throw new CustomException(
                    MessageConstant.IMAGE_UPLOAD_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
     * Generate a signed (private) URL for an authenticated image.
     *
     * @param publicId       the Cloudinary public_id
     * @param expireSeconds number of seconds until URL expires
     * @return signed HTTPS URL or null if publicId invalid
     * @author Duc Hai
     */
    @Override
    public String getPrivateUrl(String publicId, int expireSeconds) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }

        long expiration = (System.currentTimeMillis() / 1000) + expireSeconds;

        Map<String, Object> tokenOptions = new HashMap<>();
        tokenOptions.put("expiration", expiration);

        AuthToken authToken = new AuthToken(tokenOptions);

        return cloudinary.url()
                .secure(true)
                .resourceType(IMAGE)
                .type("authenticated")
                .authToken(authToken)
                .generate(publicId.trim());
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
        if (publicId == null || publicId.isBlank()) {
            return null;
        }

        return cloudinary.url()
                .secure(true)
                .resourceType(IMAGE)
//                .forceVersion(false)
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
        try{
            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            RESOURCE_TYPE, IMAGE,
                            "invalidate", true
                    )
            );
            log.info("Cloudinary image deleted: {}", publicId);
        }catch (Exception e){
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
        if (file == null || file.isEmpty()) {
            throw new CustomException(MessageConstant.IMAGE_FILE_REQUIRED, HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(MessageConstant.IMAGE_INVALID_FORMAT, HttpStatus.BAD_REQUEST);
        }

        // Check file size (10MB limit)
        long maxSize = 10L * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new CustomException(MessageConstant.IMAGE_FILE_TOO_LARGE, HttpStatus.BAD_REQUEST);
        }
    }
}
