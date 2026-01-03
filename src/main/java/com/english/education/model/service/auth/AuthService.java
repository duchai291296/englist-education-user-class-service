package com.english.education.model.service.auth;

import com.english.education.exception.AuthedException;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.auth.LoginRequest;
import com.english.education.model.dto.request.auth.RegisterRequest;
import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<?> register(RegisterRequest registerRequest) throws CustomException;
    ResponseEntity<?> login(LoginRequest loginRequest) throws AuthedException, CustomException;
    ResponseEntity<?> logout(String deviceType, Integer userId);
}
