package com.english.education.model.service;

import com.english.education.model.dto.request.RegisterRequest;
import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<?> register(RegisterRequest registerRequest);
}
