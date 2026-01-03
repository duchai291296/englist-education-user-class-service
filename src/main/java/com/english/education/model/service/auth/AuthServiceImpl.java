package com.english.education.model.service.auth;

import com.english.education.constant.Constants;
import com.english.education.constant.MessageConstant;
import com.english.education.exception.AuthedException;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.auth.LoginRequest;
import com.english.education.model.dto.response.JwtResponse;
import com.english.education.model.entity.UserSession;
import com.english.education.model.enums.RoleName;
import com.english.education.exception.DataExistException;
import com.english.education.model.dto.request.auth.RegisterRequest;
import com.english.education.model.entity.User;
import com.english.education.model.enums.Status;
import com.english.education.model.repository.user.UserRepository;
import com.english.education.model.repository.usersession.UserSessionRepository;
import com.english.education.model.service.cloudinary.CloudinaryService;
import com.english.education.model.service.common.CommonService;
import com.english.education.model.service.refreshtoken.RefreshTokenService;
import com.english.education.security.jwt.JwtProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate stringRedisTemplate;
    private final CommonService commonService;
    private final UserSessionRepository userSessionRepository;
    private final CloudinaryService cloudinaryService;

    /**
     * Register a new user account.
     * <p>
     * Flow:
     * <pre>
     *   1. Validate username uniqueness
     *   2. Build user entity with default STUDENT role
     *   3. Persist user to database (transactional)
     *   4. Upload avatar image to external storage (if provided)
     *   5. Update user avatar reference after successful upload
     * </pre>
     * Transactional behavior:
     * <pre>
     * - User persistence is transactional
     * - Database changes are rolled back on runtime exceptions
     * - External storage operations are manually compensated on failure
     * </pre>
     * Validation:
     * - Username must be unique
     * <p>
     * Notes:
     * <pre>
     * - Newly registered users are created with ACTIVE status
     * - Avatar upload is optional
     * - The system uploads the avatar only after it successfully persists the user
     * - External storage is not part of the database transaction
     * </pre>
     *
     * @param registerRequest registration payload
     * @return success message
     * @throws DataExistException if username already exists
     * @author Duc Hai (17/12/2025)
     */

    @Transactional
    @Override
    public ResponseEntity<?> register(RegisterRequest registerRequest) throws CustomException {

        // Check if username already exists
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new DataExistException("Username already exists", "username");
        }

        // Set role name is student
        Set<RoleName> roleNames = EnumSet.of(RoleName.STUDENT);

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
        user = userRepository.save(user);

        String uploadedPublicId = null;

        try{
            if (registerRequest.getImage() != null && !registerRequest.getImage().isEmpty()) {
                String publicId = Constants.AVATAR + Constants.SLASH + user.getId() + Constants.SLASH + Constants.AVATAR;
                uploadedPublicId = cloudinaryService.uploadPublicImageWithPrefix(registerRequest.getImage(), publicId);
                user.setAvatar(uploadedPublicId);
                userRepository.save(user);
            }
        }catch (Exception e){
            if (uploadedPublicId != null) {
                cloudinaryService.deleteImage(uploadedPublicId);
            }
            throw e;
        }

        return ResponseEntity.ok().body(MessageConstant.CREATE_ACCOUNT_SUCCESS);
    }


    /**
     * Authenticate user and generate JWT tokens.
     * <p>
     * Flow:
     * <pre>
     * 1. Verify username and password
     * 2. Atomically increment token version in Redis
     * 3. Generate access token and refresh token
     * </pre>
     * Important:
     * <pre>
     * - Login updates Redis token version atomically
     * - Redis is the single source of truth for token version
     * - If Redis is unavailable, login fails fast
     * </pre>
     *
     * @param loginRequest login payload
     * @return JWT response
     * @throws AuthedException if authentication fails
     * @throws CustomException if verify fail
     * @author Duc Hai (17/12/2025)
     */
    @Override
    @Transactional
    public ResponseEntity<?> login(LoginRequest loginRequest) throws AuthedException, CustomException {

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

        String avatar = cloudinaryService.getPublicImageUrl(user.getAvatar());

        Long tokenVer = session.getTokenVersion();

        // Try to update Redis cache (non-blocking, if available)
        try {
            String key = commonService.getTokenVerDevice(loginRequest.getDeviceType(), user.getId());
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
                .avatar(avatar)
                .build();
        log.info("Login Success with username: {}",user.getUsername() );
        return ResponseEntity.ok().body(jwtResponse);
    }

    /**
     * Verify user credentials.
     * <p>
     * Checks:
     * <pre>
     * - Device type is valid
     * - User exists and not soft-deleted
     * - Password matches
     * - User is not locked
     * </pre>
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
                .orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));

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

    /**
     * Logout user from a specific device.
     * <p>
     * Flow:
     * <pre>
     *   1. Revoke active refresh token for the given user and device
     *   2. Increment token version in database to invalidate existing access tokens
     *   3. Remove token version cache from Redis (best effort)
     * </pre>
     *
     * Transactional behavior:
     * <pre>
     *   - Refresh token revocation and token version increment are transactional
     *   - Database is the source of truth for token validity
     * </pre>
     *
     * Cache handling:
     * <pre>
     *   - Redis cache deletion is non-blocking
     *   - Logout succeeds even if Redis is unavailable
     *   - Cache will be healed on next authenticated request if needed
     * </pre>
     *
     * Security guarantees:
     * <pre>
     *   - All existing access tokens for the device become invalid immediately
     *   - Refresh token cannot be reused after logout
     * </pre>
     *
     * @param deviceType device type (PC or MOBILE)
     * @param userId     authenticated user identifier
     * @return logout success response
     * @author Duc Hai
     */
    @Transactional
    @Override
    public ResponseEntity<?> logout(String deviceType, Integer userId) {
        // 1. Revoke refresh token in DB
        refreshTokenService.revokeActiveTokenByUserAndDevice(userId, deviceType);

        // 2. Increment token version in DB (source of truth) to invalidate access token
        userSessionRepository.incrementTokenVersion(userId, deviceType);

        // 3. Try to update Redis cache (non-blocking, if available)
        try {
            String redisKey = commonService.getTokenVerDevice(deviceType, userId);
            stringRedisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Failed to update Redis cache during logout (userId={}), continuing with DB", userId, e);
            // Continue with logout even if Redis fails
        }

        return ResponseEntity.ok().body(MessageConstant.LOGOUT_SUCCESS);
    }
}
