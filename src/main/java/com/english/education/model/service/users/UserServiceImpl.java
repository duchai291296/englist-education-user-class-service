package com.english.education.model.service.users;

import com.english.education.constant.Constants;
import com.english.education.constant.MessageConstant;
import com.english.education.event.UserLockEvent;
import com.english.education.model.entity.User;
import com.english.education.model.enums.Status;
import com.english.education.model.repository.user.UserRepository;
import com.english.education.model.service.common.CommonServiceImpl;
import com.english.education.model.service.refreshtoken.RefreshTokenService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    @Lazy
    private RefreshTokenService refreshTokenService;
    private final CommonServiceImpl commonService;

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
     * 4. Revoke all active refresh tokens for the user
     * 5. Publish UserLockEvent to update Redis cache
     * <p>
     * Transactional behavior:
     * - All operations are executed within a single transaction
     * - If any step fails, the entire operation is rolled back
     * - User status and token revocation are atomic
     * <p>
     * Redis update:
     * - UserLockEvent is published after transaction commits
     * - Listener updates Redis: sets locked status and increments token versions
     * - Ensures authentication state is consistent between DB and Redis
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

        // Revoke all active refresh tokens for this user (PC and MOBILE)
        refreshTokenService.revokeAllTokenByUser(userId);

        // Build Redis keys for token version and lock status
        String tokenVerKeyPC = commonService.getTokenVerDevice(Constants.PC, userId);
        String tokenVerKeyMobile = commonService.getTokenVerDevice(Constants.MOBILE, userId);
        String userLocked = Constants.USER_LOCKED_KEY + userId;

        // Publish event to update Redis cache (executed after transaction commits)
        applicationEventPublisher.publishEvent(
                new UserLockEvent(tokenVerKeyPC, tokenVerKeyMobile, userLocked)
        );

        return ResponseEntity.ok().body(MessageConstant.USER_LOCKED_SUCCESS);
    }
}
