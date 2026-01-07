package com.english.education.controller;

import com.english.education.constant.MessageConstant;
import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.user.LockAndUnlockRequest;
import com.english.education.model.dto.request.user.ChangeAvatarRequest;
import com.english.education.model.dto.request.user.ChangePasswordRequest;
import com.english.education.model.dto.request.user.UpdateUserRequest;
import com.english.education.model.enums.RoleName;
import com.english.education.model.enums.Status;
import com.english.education.model.service.users.UserService;
import com.english.education.security.principle.UserDetailCustom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/user")
@CrossOrigin("*")
public class UserController {

    public static final Set<String> SORT_FIELDS = Set.of("id", "username", "email", "phone");

    private final UserService userService;

    @GetMapping("/list")
    public ResponseEntity<?> listAllUsers(@PageableDefault(page = 0, size = 20) Pageable pageable,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) Status status,
                                          @RequestParam(required = false) Set<RoleName> roles,
                                          @RequestParam(defaultValue = "id") String sortField,
                                          @RequestParam(defaultValue = "ASC") String sortDirection) throws CustomException {

        if(!SORT_FIELDS.contains(sortField)) {
            throw new CustomException(MessageConstant.INVALID_SORT_FIELD, HttpStatus.BAD_REQUEST);
        }
        Sort.Direction direction;
        try{
            direction = Sort.Direction.fromString(sortDirection.toUpperCase());
        } catch(IllegalArgumentException e) {
            throw new CustomException(MessageConstant.INVALID_SORT_DIRECTION, HttpStatus.BAD_REQUEST);
        }
        pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), direction, sortField);

        return userService.listUser(pageable, search,status,roles);
    }

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

    @PutMapping("/changePassword")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest, @AuthenticationPrincipal UserDetailCustom userDetailCustom) throws CustomException {
        return userService.changePassword(changePasswordRequest,userDetailCustom);
    }

    @PutMapping("/changeAvatar")
    public ResponseEntity<?> changeAvatar(@Valid @ModelAttribute ChangeAvatarRequest changeAvatarRequest, @AuthenticationPrincipal UserDetailCustom userDetailCustom) throws CustomException {
        return userService.changeAvatar(changeAvatarRequest,userDetailCustom);
    }

    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer userId) throws CustomException {
        return userService.deleteUser(userId);
    }

}
