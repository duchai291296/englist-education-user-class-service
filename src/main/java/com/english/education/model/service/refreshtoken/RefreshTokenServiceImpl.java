package com.english.education.model.service.refreshtoken;

import com.english.education.constant.MessageConstant;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.NewRefreshTokenRequest;
import com.english.education.model.dto.response.JwtResponse;
import com.english.education.model.entity.RefreshToken;
import com.english.education.model.entity.RefreshTokenId;
import com.english.education.model.entity.User;
import com.english.education.model.repository.RefreshTokenRepository;
import com.english.education.model.service.common.CommonServiceImpl;
import com.english.education.model.service.users.UserServiceImpl;
import com.english.education.security.jwt.JwtProvider;
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
    private final UserServiceImpl userService;
    private final JwtProvider jwtProvider;
    private final CommonServiceImpl commonServiceImpl;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String generateRefreshToken(User user, String deviceType) {
        String newRaw = UUID.randomUUID().toString();
        RefreshTokenId id = new RefreshTokenId();
        id.setUserId(user.getId());
        id.setDeviceType(deviceType);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(id);
        refreshToken.setTokenHash(hashToken(newRaw));
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(14));
        refreshToken.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);
        return newRaw;
    }

    @Override
    public ResponseEntity<?> getNewAccessToken(NewRefreshTokenRequest newRefreshTokenRequest) throws CustomException {
        try {
            ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
            UUID.fromString(newRefreshTokenRequest.getRefreshToken());
            String rawToken = hashToken(newRefreshTokenRequest.getRefreshToken());

            RefreshToken rf = refreshTokenRepository.findByTokenHash(rawToken)
                    .orElseThrow(() -> new CustomException(MessageConstant.REFRESH_TOKEN_NOT_FOUND, HttpStatus.NOT_FOUND));

            User user = userService.findById(rf.getId().getUserId());

            // Generate JWT access token
            String accessToken = jwtProvider.generateToken(user.getUsername(), user.getTokenVersion(), user.getId(), newRefreshTokenRequest.getDeviceType(), user.getRoles());

            refreshTokenRepository.delete(rf);

            String newRefreshToken = generateRefreshToken(user,newRefreshTokenRequest.getDeviceType());

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
            throw new CustomException(MessageConstant.INVALID_REFRESH_TOKEN_FORMAT, HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public RefreshToken getRefreshToken(String refreshToken) {
        return null;
    }

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
}
