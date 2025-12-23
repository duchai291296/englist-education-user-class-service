package com.english.education.model.service.refreshtoken;

import com.english.education.constant.MessageConstant;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.NewRefreshTokenRequest;
import com.english.education.model.dto.response.JwtResponse;
import com.english.education.model.entity.RefreshToken;
import com.english.education.model.entity.User;
import com.english.education.model.repository.refreshtoken.RefreshTokenRepository;
import com.english.education.model.service.common.CommonServiceImpl;
import com.english.education.model.service.users.UserService;
import com.english.education.security.jwt.JwtProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final CommonServiceImpl commonServiceImpl;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Generate a new refresh token for a specific user & device.
     * <p>
     * Design:
     * - One active refresh token per (userId, deviceType)
     * - Token is stored hashed in DB (raw token never persisted)
     * - Raw token is returned to client only once
     * - Automatically revokes any existing active token for the same (userId, deviceType)
     * <p>
     * Security notes:
     * - Using SHA-256 to hash refresh token
     * - Prevents DB leak from exposing valid tokens
     * - Ensures only one active session per device type
     *
     * @param user authenticated user
     * @param deviceType PC / MOBILE
     * @return raw refresh token (to be stored by client)
     * @author Duc Hai (21/12/2025)
     */
    @Transactional
    @Override
    public String generateRefreshToken(User user, String deviceType) {

        // Revoke any existing active token for this user and device
        // This ensures only one active token per (userId, deviceType)
        refreshTokenRepository.revokeActiveTokenByUserAndDevice(user.getId(), deviceType);

        // Generate random raw refresh token
        String newRaw = UUID.randomUUID().toString();

        // Build refresh token entity
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setDeviceType(deviceType);
        refreshToken.setTokenHash(hashToken(newRaw));
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(14));
        refreshToken.setCreatedAt(LocalDateTime.now());

        // Persist refresh token (new record with auto-increment ID)
        refreshTokenRepository.save(refreshToken);

        // Return raw token to client (only once)
        return newRaw;
    }

    /**
     * Issue a new access token using a valid refresh token.
     * <p>
     * Flow:
     * 1. Validate refresh token format
     * 2. Lookup refresh token in DB (by hashed value)
     * 3. Validate refresh token state (revoked, expired, device)
     * 4. Load user info
     * 5. Validate auth state via Redis (token version)
     * 6. Atomically revoke old refresh token
     * 7. Generate new access token
     * 8. Generate new refresh token
     * <p>
     * Concurrency safety:
     * - Uses UPDATE ... WHERE revoked = false
     * - Prevents refresh token reuse (race-condition safe)
     *
     * @param newRefreshTokenRequest refresh token payload
     * @return new access token + new refresh token
     * @author Duc Hai (21/12/2025)
     */
    @Transactional
    @Override
    public ResponseEntity<?> getNewAccessToken(NewRefreshTokenRequest newRefreshTokenRequest) throws CustomException {
        try {
            ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();

            // Validate refresh token format (UUID)
            UUID.fromString(newRefreshTokenRequest.getRefreshToken());

            // Hash raw refresh token before DB lookup
            String rawToken = hashToken(newRefreshTokenRequest.getRefreshToken());

            // Load refresh token from DB
            RefreshToken rf = refreshTokenRepository.findByTokenHash(rawToken)
                    .orElseThrow(() -> new CustomException(MessageConstant.REFRESH_TOKEN_NOT_FOUND, HttpStatus.NOT_FOUND));

            // Validate refresh token state
            validateRefreshToken(rf, newRefreshTokenRequest.getDeviceType());

            // Load user associated with refresh token
            User user = userService.findById(rf.getUserId());

            // Retrieve token version for this device from Redis
            String key = commonServiceImpl.getTokenVerDevice(newRefreshTokenRequest.getDeviceType(), user.getId());
            String tokenVerValue = ops.get(key);
            if (tokenVerValue == null) {
                throw new CustomException(
                        MessageConstant.AUTH_TRY_AGAIN_LATER,
                        HttpStatus.UNAUTHORIZED
                );
            }
            Long tokenVer = Long.parseLong(tokenVerValue);

            // Atomically revoke refresh token (race-condition safe)
            int updatedRefreshToken = refreshTokenRepository.revokeIfNotRevoked(rawToken);

            if (updatedRefreshToken == 0) {
                // Token already revoked → reuse detected
                throw new CustomException(MessageConstant.REFRESH_TOKEN_REUSED, HttpStatus.UNAUTHORIZED);
            }

            // Generate new access token
            String accessToken = jwtProvider.generateToken(user.getUsername(), tokenVer, user.getId(), newRefreshTokenRequest.getDeviceType(), user.getRoles());

            // Rotate refresh token
            String newRefreshToken = generateRefreshToken(user,newRefreshTokenRequest.getDeviceType());

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
                    .refreshToken(newRefreshToken)
                    .build();

            return ResponseEntity.ok().body(jwtResponse);
        } catch (IllegalArgumentException e) {
            // Invalid UUID format
            throw new CustomException(MessageConstant.INVALID_REFRESH_TOKEN_FORMAT, HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Hash refresh token using SHA-256.
     * <p>
     * Purpose:
     * - Prevent raw refresh token leakage if DB is compromised
     * - Enforce one-way storage
     *
     * @param rawToken plain refresh token
     * @return hashed token (hex string)
     * @author Duc Hai (21/12/2025)
     */
    private String hashToken(String rawToken) {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02X", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash token", e);
        }
    }

    /**
     * Validate refresh token state before issuing new access token.
     * <p>
     * Checks:
     * - Token is not revoked
     * - Token is not expired
     * - Token belongs to the correct device
     *
     * @param rf refresh token entity
     * @param deviceType requested device type
     * @author Duc Hai (21/12/2025)
     */
    private void validateRefreshToken(RefreshToken rf, String deviceType) throws CustomException {
        if(rf.isRevoked()){
            throw new CustomException(MessageConstant.REFRESH_TOKEN_REVOKED,HttpStatus.UNAUTHORIZED);
        }
        if(rf.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new CustomException(MessageConstant.REFRESH_TOKEN_EXPIRED,HttpStatus.UNAUTHORIZED);
        }
        if(!rf.getDeviceType().equals(deviceType)){
            throw new CustomException(MessageConstant.INVALID_DEVICE_TYPE,HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public void revokeActiveTokenByUserAndDevice(Integer userId, String deviceType) {
        refreshTokenRepository.revokeActiveTokenByUserAndDevice(userId, deviceType);
    }

    @Override
    public void revokeAllTokenByUser(Integer userId) {
        refreshTokenRepository.revokeAllActiveTokensByUser(userId);
    }
}
