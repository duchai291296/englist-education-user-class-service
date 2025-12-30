package com.english.education.model.service.cloudinary;

import com.english.education.exception.CustomException;
import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {
    /**
     * Upload an image file to Cloudinary with custom prefix (public_id).
     *
     * @param file the image file to upload
     * @param prefix the public_id prefix (e.g., "avatar/123/profile.png")
     * @return the public_id of the uploaded image
     * @throws RuntimeException if upload fails
     */
    String uploadImageWithPrefix(MultipartFile file, String prefix) throws CustomException;

    String uploadPublicImageWithPrefix(MultipartFile file, String prefix) throws CustomException;

    /**
     * Get the private/signed URL of an image by public ID.
     * Private URL requires authentication and has expiration time.
     *
     * @param publicId the public ID of the image
     * @param expireSeconds expiration time in seconds from now
     * @return the signed URL of the image
     */
    String getPrivateUrl(String publicId, int expireSeconds);

    String getPublicImageUrl(String publicId);

    void deleteImage(String publicId);
}
