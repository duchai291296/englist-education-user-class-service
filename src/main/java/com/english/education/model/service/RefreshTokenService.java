package com.english.education.model.service;

import com.english.education.model.entity.RefreshToken;
import com.english.education.model.entity.User;

public interface RefreshTokenService {
    String generateRefreshToken(User user);
}
