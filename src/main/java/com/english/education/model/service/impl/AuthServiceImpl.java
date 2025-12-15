package com.english.education.model.service.impl;

import com.english.education.constant.MessageConstant;
import com.english.education.model.enums.RoleName;
import com.english.education.exception.DataExistException;
import com.english.education.model.dto.request.RegisterRequest;
import com.english.education.model.entity.User;
import com.english.education.model.repository.UserRepository;
import com.english.education.model.service.AuthService;
import com.english.education.model.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<?> register(RegisterRequest registerRequest) {
        if (userService.existsByUsername(registerRequest.getUsername())) {
            throw new DataExistException("Username already exists", "username");
        }

        Set<RoleName> roleNames;
        roleNames = toRoleNames(registerRequest.getRoles());

        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .phone(registerRequest.getPhone())
                .email(registerRequest.getEmail())
                .fullName(registerRequest.getFullName())
                .roles(roleNames)
                .build();
        userRepository.save(user);
        return ResponseEntity.ok().body(MessageConstant.CREATE_ACCOUNT_SUCCESS);
    }

    private Set<RoleName> toRoleNames(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        return roles.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .map(role -> {
                    try {
                        return RoleName.valueOf(role);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid role: " + role);
                    }
                })
                .collect(Collectors.toSet());
    }
}
