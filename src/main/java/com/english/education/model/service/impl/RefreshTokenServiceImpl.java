package com.english.education.model.service.impl;

import com.english.education.constant.Constants;
import com.english.education.constant.MessageConstant;
import com.english.education.exception.AuthenException;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.NewRefreshTokenRequest;
import com.english.education.model.dto.response.JwtResponse;
import com.english.education.model.entity.RefreshToken;
import com.english.education.model.entity.User;
import com.english.education.model.repository.RefreshTokenRepository;
import com.english.education.model.service.AuthStateService;
import com.english.education.model.service.RefreshTokenService;
import com.english.education.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
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
    private final AuthStateService authStateService;
    private final JwtProvider jwtProvider;

    @Override
    public String generateRefreshToken(User user) {
        String newRaw = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hashToken(newRaw));
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(14));

        refreshTokenRepository.save(refreshToken);
        return newRaw;
    }

    @Override
    public ResponseEntity<?> getNewAccessToken(NewRefreshTokenRequest newRefreshTokenRequest) throws CustomException {
        try {
            UUID.fromString(newRefreshTokenRequest.getRefreshToken());
            String rawToken = hashToken(newRefreshTokenRequest.getRefreshToken());

            RefreshToken rf = refreshTokenRepository.findByTokenHash(rawToken)
                    .orElseThrow(() -> new CustomException(MessageConstant.REFRESH_TOKEN_NOT_FOUND, HttpStatus.NOT_FOUND));

            User user = userService.findById(rf.getUserId());
            String tokenVerKey = Constants.TOKEN_VER_KEY + user.getId();
            String lockedKey = Constants.USER_LOCKED_KEY + user.getId();

            // Validate authentication state from Redis
            String tokenVer = authStateService.validateAuthState(tokenVerKey, lockedKey, user);

            // Generate JWT access token
            String accessToken = jwtProvider.generateToken(user.getUsername(), tokenVer);

            refreshTokenRepository.delete(rf);

            String newRefreshToken = generateRefreshToken(user);

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
        } catch (AuthenException e) {
            throw new CustomException(
                    e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE
            );
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
