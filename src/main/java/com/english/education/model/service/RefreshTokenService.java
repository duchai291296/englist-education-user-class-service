package com.english.education.model.service;

import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.NewRefreshTokenRequest;
import com.english.education.model.entity.RefreshToken;
import com.english.education.model.entity.User;
import org.springframework.http.ResponseEntity;

public interface RefreshTokenService {
    String generateRefreshToken(User user);
    ResponseEntity<?> getNewAccessToken(NewRefreshTokenRequest newRefreshTokenRequest) throws CustomException;
    RefreshToken getRefreshToken(String refreshToken);
}
