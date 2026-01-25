package com.english.education.controller;

import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.auth.NewRefreshTokenRequest;
import com.english.education.model.service.refreshtoken.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/refreshToken")
@CrossOrigin("*")
public class RefreshTokenController {
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/newRefreshToken")
    ResponseEntity<?> newRefreshToken(@Valid @RequestBody NewRefreshTokenRequest newRefreshTokenRequest) throws CustomException {
        return refreshTokenService.getNewAccessToken(newRefreshTokenRequest);
    }
}
