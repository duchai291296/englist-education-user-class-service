package com.english.education.model.service;

import com.english.education.exception.AuthenException;
import com.english.education.model.dto.request.LoginRequest;
import com.english.education.model.dto.request.RegisterRequest;
import com.english.education.model.dto.response.JwtResponse;
import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<?> register(RegisterRequest registerRequest);
    JwtResponse login(LoginRequest loginRequest) throws AuthenException;
}
