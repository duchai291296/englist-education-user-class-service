package com.english.education.model.service.refreshtoken;

import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.auth.NewRefreshTokenRequest;
import com.english.education.model.entity.User;
import org.springframework.http.ResponseEntity;

public interface RefreshTokenService {
    String generateRefreshToken(User user, String deviceType);
    void revokeActiveTokenByUserAndDevice(Integer userId, String deviceType);
    void revokeAllTokenByUser(Integer userId);
    ResponseEntity<?> getNewAccessToken(NewRefreshTokenRequest newRefreshTokenRequest) throws CustomException;
}
