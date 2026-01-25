package com.english.education.controller;

import com.english.education.exception.AuthedException;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.auth.LoginRequest;
import com.english.education.model.dto.request.auth.LogoutRequest;
import com.english.education.model.dto.request.auth.RegisterRequest;
import com.english.education.model.service.auth.AuthService;
import com.english.education.security.principle.UserDetailCustom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/auth")
@CrossOrigin("*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @ModelAttribute RegisterRequest registerRequest) throws CustomException {
        return authService.register(registerRequest);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) throws AuthedException, CustomException {
        return authService.login(loginRequest);
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody LogoutRequest logoutRequest, @AuthenticationPrincipal UserDetailCustom userDetailCustom) throws AuthedException, CustomException {
        return authService.logout(logoutRequest.getDeviceType(), userDetailCustom.getUserId());
    }
}
