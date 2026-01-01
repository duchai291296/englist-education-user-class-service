package com.english.education.controller;

import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.LockAndUnlockRequest;
import com.english.education.model.dto.request.user.UpdateUserRequest;
import com.english.education.model.service.users.UserService;
import com.english.education.security.principle.UserDetailCustom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @GetMapping("/detail/{userId}")
    public ResponseEntity<?> getUserDetail(@PathVariable Integer userId) {
        return userService.detail(userId);
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateUser(@Valid @RequestBody UpdateUserRequest updateUserRequest, @AuthenticationPrincipal UserDetailCustom userDetailCustom) throws CustomException {
        return userService.updateUser(updateUserRequest,userDetailCustom);
    }
}
