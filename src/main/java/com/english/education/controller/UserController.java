package com.english.education.controller;

import com.english.education.model.dto.request.LockAndUnlockRequest;
import com.english.education.model.service.users.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/user")
@CrossOrigin("*")
public class UserController {

    private final UserService userService;

    @PutMapping("/lockUser")
    public ResponseEntity<?> lockUser(@Valid @RequestBody LockAndUnlockRequest lockAndUnlockRequest) {
        return userService.lockUser(lockAndUnlockRequest.getUserId());
    }

    @PutMapping("/unlockUser")
    public ResponseEntity<?> unlockUser(@Valid @RequestBody LockAndUnlockRequest lockAndUnlockRequest) {
        return userService.unlockUser(lockAndUnlockRequest.getUserId());
    }
}
