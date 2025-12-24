package com.english.education.model.service.users;

import com.english.education.constant.Constants;
import com.english.education.constant.MessageConstant;
import com.english.education.model.entity.User;
import com.english.education.model.enums.Status;
import com.english.education.model.repository.refreshtoken.RefreshTokenRepository;
import com.english.education.model.repository.user.UserRepository;
import com.english.education.model.repository.usersession.UserSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final StringRedisTemplate stringRedisTemplate;

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

    public String tokenKey(Integer userId, String deviceType) {
        if (deviceType.equals(Constants.PC)){
            return Constants.TOKEN_VER_PC + userId;
        }else{
            return Constants.TOKEN_VER_MOBILE + userId;
        }
    }
}
