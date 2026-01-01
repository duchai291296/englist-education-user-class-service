package com.english.education.model.service.users;

import com.english.education.annotation.LogAction;
import com.english.education.constant.Constants;
import com.english.education.constant.MessageConstant;
import com.english.education.exception.CustomException;
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
import com.english.education.security.principle.UserDetailCustom;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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

    @Override
    public User findById(Integer id) {
        return userRepository.findById(id).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));
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
     */
    @Transactional
    @Override
    @LogAction("UPDATE_USER")
    public ResponseEntity<?> updateUser(UpdateUserRequest request, UserDetailCustom userDetailCustom) throws CustomException {

        boolean isAdmin = userDetailCustom.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleName.ADMIN.name()));

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

    public String tokenKey(Integer userId, String deviceType) {
        if (deviceType.equals(Constants.PC)) {
            return Constants.TOKEN_VER_PC + userId;
        } else {
            return Constants.TOKEN_VER_MOBILE + userId;
        }
    }
}
