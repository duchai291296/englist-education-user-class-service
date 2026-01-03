package com.english.education.model.service.users;

import com.english.education.annotation.LogAction;
import com.english.education.constant.Constants;
import com.english.education.constant.MessageConstant;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.user.ChangeAvatarRequest;
import com.english.education.model.dto.request.user.ChangePasswordRequest;
import com.english.education.model.dto.request.user.UpdateUserRequest;
import com.english.education.model.dto.response.DataResponse;
import com.english.education.model.dto.response.user.UserDetailResponse;
import com.english.education.model.entity.User;
import com.english.education.model.enums.RoleName;
import com.english.education.model.enums.Status;
import com.english.education.model.repository.refreshtoken.RefreshTokenRepository;
import com.english.education.model.repository.user.UserRepository;
import com.english.education.model.repository.usersession.UserSessionRepository;
import com.english.education.model.service.cloudinary.CloudinaryService;
import com.english.education.model.service.common.CommonService;
import com.english.education.security.principle.UserDetailCustom;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final CloudinaryService cloudinaryService;
    private final CommonService commonService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User findById(Integer id) {
        return userRepository.findById(id).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));
    }

    /**
     * Lock a user account by setting status to INACTIVE.
     * <p>
     * Flow:
     * 1. Find user by ID (excluding soft-deleted users)
     * 2. Check if user is already locked (idempotent)
     * 3. Set user status to INACTIVE
     * 4. Increment token version for all user sessions (PC and MOBILE) to invalidate existing tokens
     * 5. Revoke all active refresh tokens for the user
     * 6. Clear Redis token version cache
     * <p>
     * Transactional behavior:
     * - All operations are executed within a single transaction
     * - If any step fails, the entire operation is rolled back
     * - User status, token version increment, and token revocation are atomic
     * <p>
     * Token invalidation:
     * - Incrementing token version in DB invalidates all existing access tokens
     * - Redis cache is cleared to force validation against DB
     * - Ensures locked users cannot use existing tokens even if Redis is unavailable
     * <p>
     * Idempotent:
     * - If user is already locked, returns success message without side effects
     *
     * @param userId user ID to lock
     * @return success message or "already locked" message
     * @throws NoSuchElementException if user not found or user is soft-deleted
     * @author Duc Hai (22/12/2025)
     */
    @Transactional
    @Override
    public ResponseEntity<?> lockUser(Integer userId) {
        // Find user (excluding soft-deleted users)
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));

        // Idempotent check: if already locked, return early
        if (user.getStatus() == Status.INACTIVE) {
            return ResponseEntity.ok().body(MessageConstant.USER_ALREADY_LOCKED);
        }

        // Set user status to INACTIVE
        user.setStatus(Status.INACTIVE);
        userRepository.save(user);

        // Set user session to INACTIVE
        userSessionRepository.updateStatusByUserId(userId, Status.INACTIVE);

        // Increment token version for all user sessions (PC and MOBILE)
        // This invalidates all existing access tokens immediately
        userSessionRepository.incrementTokenVersionByUserId(userId);

        // Revoke all active refresh tokens for this user (PC and MOBILE)
        refreshTokenRepository.revokeAllActiveTokensByUser(userId);

        // Clear Redis token version cache to force validation against DB
        // Best-effort operation: failure does not block the lock operation
        try {
            Set<String> keys = stringRedisTemplate.keys(
                    Constants.TOKEN_VER_PREFIX + "*:" + userId
            );
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Failed to clear Redis token cache when locking userId={}", userId, e);
        }

        return ResponseEntity.ok().body(MessageConstant.USER_LOCKED_SUCCESS);
    }

    /**
     * Unlock a user account by setting status to ACTIVE.
     * <p>
     * Flow:
     * 1. Find user by ID (excluding soft-deleted users)
     * 2. Set user status to ACTIVE
     * 3. Update all user session statuses to ACTIVE (PC and MOBILE)
     * 4. Clear Redis token version cache
     * <p>
     * Transactional behavior:
     * - All operations are executed within a single transaction
     * - If any step fails, the entire operation is rolled back
     * - User status and session status updates are atomic
     * <p>
     * Token invalidation:
     * - Token version is NOT incremented (intentionally)
     * - Existing tokens remain INVALID even after unlock
     * - User must log in again to obtain new valid tokens
     * - This ensures security: tokens issued before lock cannot be reused
     * <p>
     * Redis cache:
     * - Cache is cleared to avoid stale data
     * - New token version will be set in Redis when user logs in again
     * - Best-effort operation: failure does not block the unlock operation
     *
     * @param userId user ID to unlock
     * @return success message
     * @throws NoSuchElementException if user not found or user is soft-deleted
     * @author Duc Hai (22/12/2025)
     */
    @Transactional
    @Override
    public ResponseEntity<?> unlockUser(Integer userId) {
        // Find user (excluding soft-deleted users)
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));

        if (user.getStatus() == Status.ACTIVE) {
            return ResponseEntity.ok().body(MessageConstant.USER_ALREADY_UNLOCKED);
        }

        // Set user status to ACTIVE
        user.setStatus(Status.ACTIVE);
        userRepository.save(user);

        // Update all user session statuses to ACTIVE (PC and MOBILE)
        // This ensures consistency between User and UserSession status
        userSessionRepository.updateStatusByUserId(userId, Status.ACTIVE);

        // Note: Token version is NOT incremented
        // - Existing tokens remain INVALID even after unlock
        // - User must log in again to obtain new valid tokens
        // - This ensures security: tokens issued before lock cannot be reused

        // Clear Redis token version cache to avoid stale data
        // Best-effort operation: failure does not block the unlock operation
        try {
            stringRedisTemplate.delete(List.of(
                    tokenKey(userId, "PC"),
                    tokenKey(userId, "MOBILE")
            ));
        } catch (Exception e) {
            log.warn("Failed to clear Redis cache on unlock userId={}", userId, e);
        }

        return ResponseEntity.ok().body(MessageConstant.USER_UNLOCKED_SUCCESS);
    }

    /**
     * Get user detail by user ID.
     * <p>
     * Flow:
     * 1. Find user by ID (excluding soft-deleted users)
     * 2. Return user entity as response
     * <p>
     * Behavior:
     * - Only returns users that are not soft-deleted
     * - Throws exception if user does not exist
     * <p>
     * Security:
     * - Authorization is assumed to be handled at controller / filter level
     * - This method does NOT perform permission checks
     * <p>
     * Notes:
     * - Sensitive fields should be protected using @JsonIgnore / @JsonIgnoreProperties
     * - Returned entity must be safe for exposure
     *
     * @param userId user ID to retrieve
     * @return user detail
     * @throws NoSuchElementException if user not found or user is soft-deleted+
     * @author Duc Hai
     */
    @Override
    public ResponseEntity<?> detail(Integer userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));
        String avatar = cloudinaryService.getPublicImageUrl(user.getAvatar());
        UserDetailResponse userDetailResponse = new UserDetailResponse();
        userDetailResponse.setId(user.getId());
        userDetailResponse.setUserName(user.getUsername());
        userDetailResponse.setPhone(user.getPhone());
        userDetailResponse.setEmail(user.getEmail());
        userDetailResponse.setRoles(user.getRoles());
        userDetailResponse.setFullName(user.getFullName());
        userDetailResponse.setAvatar(avatar);
        return ResponseEntity.ok().body(new DataResponse<>(200,null, userDetailResponse));
    }

    /**
     * Update user information.
     * <p>
     * Flow:
     * 1. Determine whether current user is ADMIN
     * 2. Verify update permission:
     * - ADMIN can update any user
     * - Non-admin can only update their own profile
     * 3. Find target user (excluding soft-deleted users)
     * 4. Update basic user information (fullName, email, phone)
     * 5. If ADMIN:
     * - Validate roles
     * - Update user roles
     * 6. Persist user changes
     * <p>
     * Transactional behavior:
     * - User data update is transactional
     * - Database changes are rolled back on exception
     * <p>
     * Security:
     * - Non-admin users are forbidden from updating other users
     * - Only ADMIN users can update roles
     * - This method rejects invalid role values
     * <p>
     * Validation:
     * - Role list must contain only valid RoleName enum values
     *
     * @param request          update user request payload
     * @param userDetailCustom authenticated user details
     * @return updated user information
     * @throws CustomException        if permission denied or invalid role
     * @throws NoSuchElementException if user not found or soft-deleted
     * @author Duc Hai (3/1/2026)
     */
    @Transactional
    @Override
    @LogAction("UPDATE_USER")
    public ResponseEntity<?> updateUser(UpdateUserRequest request, UserDetailCustom userDetailCustom) throws CustomException {

        boolean isAdmin = commonService.isAdmin(userDetailCustom);

        // Authorization check
        if (!isAdmin && !Objects.equals(request.getUserUpdateId(), userDetailCustom.getUserId())) {
            throw new CustomException(MessageConstant.CAN_NOT_UPDATE_THIS_USER, HttpStatus.FORBIDDEN);
        }

        // Find target user
        User user = userRepository.findByIdAndDeletedAtIsNull(request.getUserUpdateId()).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());

        // Update roles (ADMIN only)
        if (isAdmin && request.getRoles() != null) {
            if (!EnumSet.allOf(RoleName.class).containsAll(request.getRoles())) {
                throw new CustomException(MessageConstant.INVALID_ROLE, HttpStatus.BAD_REQUEST);
            }
            user.setRoles(new HashSet<>(request.getRoles()));
        }

        // Persist user changes
        userRepository.save(user);

        return ResponseEntity.ok().body(new DataResponse<>(200, MessageConstant.USER_UPDATE_SUCCESS, user));
    }

    /**
     * Change user password.
     * <p>
     * Flow:
     * 1. Determine whether current user is ADMIN
     * 2. Resolve target user ID:
     *    - ADMIN can change password of any user (userId is required)
     *    - Non-admin can only change their own password (userId must not be provided)
     * 3. Find target user (excluding soft-deleted users)
     * 4. Validate password change rules
     * 5. Encode and update new password
     * 6. Persist password changes
     * <p>
     * Transactional behavior:
     * - Password update is executed within a database transaction
     * - Database changes are rolled back on runtime exceptions
     * <p>
     * Security:
     * - Non-admin users are forbidden from changing other users' passwords
     * - ADMIN users are allowed to change passwords without providing old password
     * <p>
     * Validation:
     * - New password and confirm password must match
     * - Non-admin users must provide correct old password
     *
     * @param request          change password request payload
     * @param userDetailCustom authenticated user details
     * @return response indicating password change success
     * @throws CustomException        if validation fails or permission is denied
     * @throws NoSuchElementException if target user does not exist or is soft-deleted
     * @author Duc Hai (3/1/2026)
     */
    @Transactional
    @Override
    public ResponseEntity<?> changePassword(ChangePasswordRequest request, UserDetailCustom userDetailCustom) throws CustomException {
        // Check whether current user has ADMIN role
        boolean isAdmin = commonService.isAdmin(userDetailCustom);

        // Resolve which user is allowed to be updated based on role
        // ADMIN  -> target user comes from request
        // USER   -> target user is always the authenticated user
        Integer targetUserId = resolveTargetUserId(request.getUserId(),userDetailCustom,isAdmin);

        // Load target user, excluding soft-deleted records
        User user = userRepository.findByIdAndDeletedAtIsNull(targetUserId).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));

        // Validate password change rules (confirm password, old password, permission)
        validateChangePassword(request,isAdmin, user);

        // Encode and update new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // Persist password change
        userRepository.save(user);

        return ResponseEntity.ok().body(new DataResponse<>(200, MessageConstant.CHANGE_PASSWORD_SUCCESS, null));
    }

    /**
     * Change or delete user avatar.
     * <p>
     * Flow:
     * 1. Determine whether current user is ADMIN
     * 2. Resolve target user ID:
     *    - ADMIN can update avatar of any user
     *    - Non-admin can only update their own avatar
     * 3. Find target user (excluding soft-deleted users)
     * 4. Validate avatar action:
     *    - Reject request that attempts to delete and upload avatar at the same time
     * 5. Handle avatar update:
     *    - If delete flag is set, remove avatar reference and delete image
     *    - If upload is requested, upload new avatar and update reference
     * <p>
     * Transactional behavior:
     * - User avatar reference update is transactional
     * - Database changes are rolled back on runtime exceptions
     * <p>
     * External IO:
     * - Avatar upload and deletion are performed via Cloudinary
     * - The service manually compensates external storage operations on failure
     * <p>
     * Security:
     * - Non-admin users are forbidden from updating other users' avatars
     *
     * @param request          change avatar request payload
     * @param userDetailCustom authenticated user details
     * @return response indicating avatar change success
     * @throws CustomException        if validation fails or permission is denied
     * @throws NoSuchElementException if target user does not exist or is soft-deleted
     * @author Duc Hai (3/1/2026)
     */
    @Transactional
    @Override
    public ResponseEntity<?> changeAvatar(ChangeAvatarRequest request, UserDetailCustom userDetailCustom) throws CustomException {

        // Check whether current user has ADMIN role
        boolean isAdmin = commonService.isAdmin(userDetailCustom);

        // Resolve which user's avatar can be modified
        // ADMIN  -> target user comes from request
        // USER   -> target user is always the authenticated user
        Integer targetUserId = resolveTargetUserId(request.getUserId(),userDetailCustom,isAdmin);

        // Load target user, excluding soft-deleted records
        User user = userRepository.findByIdAndDeletedAtIsNull(targetUserId).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));

        // Keep track of uploaded image to manually rollback external IO on failure
        String uploadedPublicId = null;

        // Reject conflicting intent:
        // Client must not request delete and upload avatar at the same time
        if (request.isDelete() && request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            throw new CustomException(
                    MessageConstant.INVALID_AVATAR_ACTION,
                    HttpStatus.BAD_REQUEST
            );
        }

        // Handle avatar deletion
        if (request.isDelete() && user.getAvatar() != null) {
            // Store old avatar reference before removal
            String oldAvatar = user.getAvatar();

            // Remove avatar reference from database (source of truth)
            user.setAvatar(null);
            userRepository.save(user);

            // Delete avatar from external storage (Cloudinary)
            cloudinaryService.deleteImage(oldAvatar);
        } else {
            try{
                // Handle avatar upload
                if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
                    // Generate deterministic publicId for avatar
                    String publicId = Constants.AVATAR + Constants.SLASH + user.getId() + Constants.SLASH + Constants.AVATAR;

                    // Upload avatar to Cloudinary
                    uploadedPublicId = cloudinaryService.uploadPublicImageWithPrefix(request.getAvatar(), publicId);

                    // Update avatar reference in database
                    user.setAvatar(uploadedPublicId);
                    userRepository.save(user);
                }
            }catch (Exception e){
                // Manual compensation:
                // If upload succeeded but DB update failed, delete uploaded image
                if (uploadedPublicId != null) {
                    cloudinaryService.deleteImage(uploadedPublicId);
                }
                // Rethrow to trigger transaction rollback
                throw e;
            }
        }
        return ResponseEntity.ok().body(new DataResponse<>(200,MessageConstant.CHANGE_AVATAR_SUCCESS, null));
    }

    @Transactional
    @Override
    public ResponseEntity<?> deleteUser(Integer userId) throws CustomException {

        User user = userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));

        if (user.getRoles().contains(RoleName.ADMIN)){
            throw new CustomException(MessageConstant.CANNOT_DELETE_ADMIN,HttpStatus.FORBIDDEN);
        }

        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        userSessionRepository.updateStatusByUserId(userId, Status.INACTIVE);

        refreshTokenRepository.revokeAllActiveTokensByUser(userId);

        try {
            stringRedisTemplate.delete(List.of(
                    tokenKey(userId, Constants.PC),
                    tokenKey(userId, Constants.MOBILE)
            ));
        } catch (Exception e) {
            log.warn("Failed to clear Redis cache on delete userId={}", userId, e);
        }

        return ResponseEntity.ok().body(new DataResponse<>(200,MessageConstant.DELETE_USER_SUCCESS, null));
    }

    /**
     * Resolve target user ID based on current user's role.
     * <p>
     * Rules:
     * - ADMIN users must provide userId and can operate on any user
     * - Non-admin users must not provide userId and can only operate on themselves
     * <p>
     * Security:
     * - Prevents privilege escalation by rejecting invalid userId usage
     *
     * @param userId           user ID provided in request
     * @param userDetailCustom authenticated user details
     * @param isAdmin          whether current user has ADMIN role
     * @return resolved target user ID
     * @throws CustomException if userId usage violates authorization rules
     * @author Duc Hai (3/1/2026)
     */
    private Integer resolveTargetUserId(
            Integer userId,
            UserDetailCustom userDetailCustom,
            boolean isAdmin
    ) throws CustomException {

        // ADMIN users must explicitly specify which user they want to operate on
        if (isAdmin) {
            if (userId == null) {
                throw new CustomException(
                        MessageConstant.USER_ID_REQUIRED,
                        HttpStatus.BAD_REQUEST
                );
            }
            return userId;
        }

        // Non-admin users are NOT allowed to specify userId
        // This prevents privilege escalation via crafted requests
        if (userId != null) {
            throw new CustomException(
                    MessageConstant.USER_ID_NOT_ALLOWED,
                    HttpStatus.BAD_REQUEST
            );
        }
        // Non-admin users can only operate on their own account
        return userDetailCustom.getUserId();
    }

    /**
     * Validate password change request.
     * <p>
     * Validation rules:
     * - New password must match confirm password
     * - Non-admin users must provide old password
     * - Old password must match the current password in database
     * <p>
     * Security:
     * - Prevents unauthorized password changes
     *
     * @param request change password request payload
     * @param isAdmin whether current user has ADMIN role
     * @param user    target user entity
     * @throws CustomException if validation fails
     * @author Duc Hai (3/1/2026)
     */
    private void validateChangePassword(ChangePasswordRequest request, boolean isAdmin, User user) throws CustomException {
        // New password and confirm password must match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException(
                    MessageConstant.CONFIRM_PASSWORD_NOT_MATCH,
                    HttpStatus.BAD_REQUEST
            );
        }

        // Additional validation for non-admin users
        if (!isAdmin) {

            // Old password is mandatory for non-admin users
            if (request.getOldPassword() == null) {
                throw new CustomException(
                        MessageConstant.OLD_PASSWORD_CANNOT_BE_NULL,
                        HttpStatus.BAD_REQUEST
                );
            }

            // Old password must match the current password in database
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new CustomException(
                        MessageConstant.THE_OLD_PASSWORD_IS_INCORRECT,
                        HttpStatus.BAD_REQUEST
                );
            }
        }

    }

    /**
     * Generate Redis token version key based on user ID and device type.
     * <p>
     * Purpose:
     * - Separate token versions for different device types
     * - Used for session invalidation and token management
     *
     * @param userId     user ID
     * @param deviceType device type (PC or MOBILE)
     * @return token version key for Redis storage
     * @author Duc Hai
     */
    public String tokenKey(Integer userId, String deviceType) {

        // Generate Redis token version key based on device type
        // Used to invalidate tokens per device (PC / MOBILE)
        if (deviceType.equals(Constants.PC)) {
            return Constants.TOKEN_VER_PC + userId;
        } else {
            return Constants.TOKEN_VER_MOBILE + userId;
        }
    }
}
