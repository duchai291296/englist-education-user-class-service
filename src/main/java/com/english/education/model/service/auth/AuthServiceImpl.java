package com.english.education.model.service.auth;

import com.english.education.constant.Constants;
import com.english.education.constant.MessageConstant;
import com.english.education.event.UserCreatedEvent;
import com.english.education.exception.AuthenException;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.LoginRequest;
import com.english.education.model.dto.response.JwtResponse;
import com.english.education.model.entity.UserSession;
import com.english.education.model.enums.RoleName;
import com.english.education.exception.DataExistException;
import com.english.education.model.dto.request.RegisterRequest;
import com.english.education.model.entity.User;
import com.english.education.model.enums.Status;
import com.english.education.model.repository.user.UserRepository;
import com.english.education.model.repository.usersession.UserSessionRepository;
import com.english.education.model.service.common.CommonServiceImpl;
import com.english.education.model.service.refreshtoken.RefreshTokenService;
import com.english.education.security.jwt.JwtProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final CommonServiceImpl commonServiceImpl;
    private final UserSessionRepository userSessionRepository;

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
     * 2. Atomically increment token version in Redis
     * 3. Generate access token and refresh token
     * <p>
     * Important:
     * - Login updates Redis token version atomically
     * - Redis is the single source of truth for token version
     * - If Redis is unavailable, login fails fast
     *
     * @param loginRequest login payload
     * @return JWT response
     * @throws AuthenException if authentication fails
     * @throws CustomException if verify fail
     * @author Duc Hai (17/12/2025)
     */
    @Override
    @Transactional
    public ResponseEntity<?> login(LoginRequest loginRequest) throws AuthenException, CustomException {

        // Verify username and password using DB
        User user = verify(loginRequest);

        // Generate refresh token
        String refreshToken = refreshTokenService.generateRefreshToken(user, loginRequest.getDeviceType());

        // Atomically upsert (insert or update) user session and increment token version in one query
        // This prevents race conditions when multiple concurrent login requests occur
        // - If session exists: increment token_version
        // - If session doesn't exist: create with token_version = 1
        userSessionRepository.upsertAndIncrementTokenVersion(
                user.getId(),
                loginRequest.getDeviceType(),
                user.getStatus().name()
        );

        // Get updated session from DB to retrieve new token version
        // clearAutomatically ensures we get fresh data from DB
        UserSession session = userSessionRepository.findByUserIdAndDeviceType(
                user.getId(),
                loginRequest.getDeviceType()
        ).orElseThrow(() -> new RuntimeException("Session not found after upsert"));

        Long tokenVer = session.getTokenVersion();

        // Try to update Redis cache (non-blocking, if available)
        try {
            String key = commonServiceImpl.getTokenVerDevice(loginRequest.getDeviceType(), user.getId());
            stringRedisTemplate.opsForValue().set(key, String.valueOf(tokenVer));
        } catch (Exception e) {
            log.warn("Failed to update Redis cache during login (userId={}), continuing with DB", user.getId(), e);
            // Continue with login even if Redis fails
        }

        // Generate JWT access token
        String accessToken = jwtProvider.generateToken(user.getUsername(), tokenVer, user.getId(), loginRequest.getDeviceType(), user.getRoles());

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
     * - Device type is valid
     * - User exists and not soft-deleted
     * - Password matches
     * - User is not locked
     *
     * @param loginRequest login payload
     * @return authenticated user entity
     * @throws CustomException if device type is invalid
     * @author Duc Hai (17/12/2025)
     */
    private User verify(LoginRequest loginRequest) throws CustomException {

        // Check device type
        if (!Objects.equals(loginRequest.getDeviceType(), Constants.PC) && !Objects.equals(loginRequest.getDeviceType(), Constants.MOBILE)) {
            throw new CustomException(MessageConstant.INVALID_DEVICE_TYPE, HttpStatus.BAD_REQUEST);
        }

        // Fetch user that is not soft-deleted
        User user = userRepository.findByUsernameAndDeletedAtIsNull(loginRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(MessageConstant.USER_NOT_FOUND));

        // Validate password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new CustomException(MessageConstant.INVALID_USER_NAME_OR_PASSWORD, HttpStatus.BAD_REQUEST);
        }

        // Check locked user
        if (user.getStatus() == Status.INACTIVE) {
            throw new CustomException(
                    MessageConstant.USER_IS_LOCKED,
                    HttpStatus.BAD_REQUEST
            );
        }

        return user;
    }

    @Transactional
    @Override
    public ResponseEntity<?> logout(String deviceType, Integer userId) {
        // 1. Revoke refresh token in DB
        refreshTokenService.revokeActiveTokenByUserAndDevice(userId, deviceType);

        // 2. Increment token version in DB (source of truth) to invalidate access token
        userSessionRepository.incrementTokenVersion(userId, deviceType);

        // 3. Try to update Redis cache (non-blocking, if available)
        try {
            String redisKey = commonServiceImpl.getTokenVerDevice(deviceType, userId);
            stringRedisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Failed to update Redis cache during logout (userId={}), continuing with DB", userId, e);
            // Continue with logout even if Redis fails
        }

        return ResponseEntity.ok().body(MessageConstant.LOGOUT_SUCCESS);
    }
}
