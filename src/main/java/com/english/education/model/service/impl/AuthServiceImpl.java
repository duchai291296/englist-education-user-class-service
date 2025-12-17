package com.english.education.model.service.impl;

import com.english.education.constant.MessageConstant;
import com.english.education.event.UserCreatedEvent;
import com.english.education.exception.AuthenException;
import com.english.education.model.dto.request.LoginRequest;
import com.english.education.model.dto.response.JwtResponse;
import com.english.education.model.enums.RoleName;
import com.english.education.exception.DataExistException;
import com.english.education.model.dto.request.RegisterRequest;
import com.english.education.model.entity.User;
import com.english.education.model.enums.Status;
import com.english.education.model.repository.UserRepository;
import com.english.education.model.service.AuthService;
import com.english.education.model.service.RefreshTokenService;
import com.english.education.security.jwt.JwtProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication service implementation.
 * <p>
 * Responsibilities:
 * - Register new users
 * - Authenticate users
 * - Generate JWT access & refresh tokens
 * - Validate authentication state using Redis cache
 * <p>
 * Design decisions:
 * - Database is the source of truth
 * - Redis is used as authentication cache only
 * - Login flow NEVER rebuilds Redis state
 * - Redis is updated only after successful DB commit
 *
 * @author Duc Hai (17/12/2025)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Register a new user account.
     * <p>
     * Flow:
     * 1. Validate username uniqueness
     * 2. Persist user to database (transactional)
     * 3. Publish UserCreatedEvent after successful commit
     * <p>
     * Redis initialization is handled asynchronously
     * via UserAuthCacheListener after transaction commit.
     *
     * @param registerRequest registration payload
     * @return success message
     * @throws DataExistException if username already exists
     * @author Duc Hai (17/12/2025)
     */

    @Transactional
    @Override
    public ResponseEntity<?> register(RegisterRequest registerRequest) {

        // Check if username already exists
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new DataExistException("Username already exists", "username");
        }

        // Convert role names from request to RoleName enum
        Set<RoleName> roleNames = toRoleNames(registerRequest.getRoles());

        // Build user entity
        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .phone(registerRequest.getPhone())
                .email(registerRequest.getEmail())
                .fullName(registerRequest.getFullName())
                .tokenVersion(1)
                .status(Status.ACTIVE)
                .roles(roleNames)
                .build();
        // Persist user to database
        userRepository.save(user);

        // Publish event to initialize authentication cache.
        // Listener will:
        // - Wait until transaction commits
        // - Then initialize Redis auth keys
        //
        // If transaction rolls back, the event will not be processed.
        applicationEventPublisher.publishEvent(
                new UserCreatedEvent(user.getId())
        );

        return ResponseEntity.ok().body(MessageConstant.CREATE_ACCOUNT_SUCCESS);
    }


    /**
     * Authenticate user and generate JWT tokens.
     * <p>
     * Flow:
     * 1. Verify username and password
     * 2. Validate authentication state from Redis
     * 3. Generate access token and refresh token
     * <p>
     * Important:
     * - Login NEVER modifies Redis
     * - If Redis state is missing or inconsistent, login fails fast
     *
     * @param loginRequest login payload
     * @return JWT response
     * @throws AuthenException if authentication fails
     * @author Duc Hai (17/12/2025)
     */
    @Override
    public ResponseEntity<?> login(LoginRequest loginRequest) throws AuthenException {

        // Verify username and password using DB
        User user = verify(loginRequest);

        // Redis authentication keys
        String tokenVerKey = "token_ver:" + user.getId();
        String lockedKey = "user_locked:" + user.getId();

        // Validate authentication state from Redis
        String tokenVer = validateAuthState(tokenVerKey, lockedKey, user);

        // Generate JWT access token
        String accessToken = jwtProvider.generateToken(user.getUsername(), tokenVer);
        // Generate refresh token
        String refreshToken = refreshTokenService.generateRefreshToken(user);

        // Build response
        JwtResponse jwtResponse = JwtResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .roles(user.getRoles())
                .status(user.getStatus())
                .refreshToken(refreshToken)
                .build();
        return ResponseEntity.ok().body(jwtResponse);
    }

    /**
     * Convert role strings to {@link RoleName} enum.
     *
     * @param roles role names from request
     * @return set of RoleName
     * @throws IllegalArgumentException if role is invalid
     * @author Duc Hai (17/12/2025)
     */
    private Set<RoleName> toRoleNames(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        return roles.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .map(role -> {
                    try {
                        return RoleName.valueOf(role);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid role: " + role);
                    }
                })
                .collect(Collectors.toSet());
    }

    /**
     * Verify user credentials.
     * <p>
     * Checks:
     * - User exists and not deleted
     * - Password matches
     *
     * @param loginRequest login payload
     * @return authenticated user entity
     * @throws AuthenException if credentials are invalid
     * @author Duc Hai (17/12/2025)
     */
    private User verify(LoginRequest loginRequest) throws AuthenException {

        // Fetch user that is not soft-deleted
        User user = userRepository.findByUsernameAndDeletedAtIsNull(loginRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(MessageConstant.USER_NOT_FOUND));

        // Validate password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new AuthenException(MessageConstant.INVALID_USER_NAME_OR_PASSWORD, "password");
        }

        return user;
    }

    /**
     * Validate authentication state from Redis cache.
     * <p>
     * Rules:
     * - Both token version and lock status must exist
     * - User must not be locked
     * <p>
     * This method is READ-ONLY.
     * It never rebuilds or modifies Redis state.
     * <p>
     * If Redis data is missing or inconsistent, the request fails fast
     * to prevent incorrect authentication state.
     *
     * @param tokenVerKey Redis key storing token version
     * @param lockedKey   Redis key storing user lock status
     * @param user        authenticated user
     * @return token version
     * @throws AuthenException if auth state is invalid or user is locked
     * @author Duc Hai (17/12/2025)
     */
    private String validateAuthState(String tokenVerKey, String lockedKey, User user) throws AuthenException {

        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();

        String tokenVer = ops.get(tokenVerKey);
        String locked = ops.get(lockedKey);

        // Redis cache not ready or just restarted
        if (tokenVer == null || locked == null) {
            log.warn("Auth cache missing for userId={}", user.getId());
            throw new AuthenException(
                    MessageConstant.AUTH_TRY_AGAIN_LATER,
                    "system"
            );
        }

        // User has been locked by admin
        if (Status.INACTIVE.name().equals(locked)) {
            throw new AuthenException(
                    MessageConstant.USER_IS_LOCKED,
                    "username"
            );
        }

        return tokenVer;

    }
}
