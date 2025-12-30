package com.english.education.model.service.auth;

import com.english.education.exception.AuthenException;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.LoginRequest;
import com.english.education.model.dto.request.RegisterRequest;
import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<?> register(RegisterRequest registerRequest) throws CustomException;
    ResponseEntity<?> login(LoginRequest loginRequest) throws AuthenException, CustomException;
    ResponseEntity<?> logout(String deviceType, Integer userId);
}
