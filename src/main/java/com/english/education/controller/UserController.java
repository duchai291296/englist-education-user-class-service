package com.english.education.controller;

import com.english.education.model.service.users.UserService;
import com.english.education.security.principle.UserDetailCustom;
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
    public ResponseEntity<?> lockUser(@AuthenticationPrincipal UserDetailCustom userDetailCustom) {
        return userService.lockUser(userDetailCustom.getUserId());
    }
}
