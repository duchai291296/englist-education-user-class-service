package com.english.education.model.service.impl;

import com.english.education.constant.MessageConstant;
import com.english.education.exception.AuthenException;
import com.english.education.model.dto.request.LoginRequest;
import com.english.education.model.dto.response.JwtResponse;
import com.english.education.model.enums.RoleName;
import com.english.education.exception.DataExistException;
import com.english.education.model.dto.request.RegisterRequest;
import com.english.education.model.entity.User;
import com.english.education.model.enums.Status;
import com.english.education.model.repository.UserRepository;
import com.english.education.model.service.AuthService;
import com.english.education.model.service.UserService;
import com.english.education.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final JwtProvider jwtProvider;

    @Override
    public ResponseEntity<?> register(RegisterRequest registerRequest) {

        if (userRepository.existsByUsername(registerRequest.getUsername())) {
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

    @Override
    public JwtResponse login(LoginRequest loginRequest) throws AuthenException {

        User user = verify(loginRequest);

        String accessToken = jwtProvider.generateToken(user.getUsername());
        return null;
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

    private User verify(LoginRequest loginRequest) throws AuthenException {
        User user = userRepository.findByUsernameAndDeletedAtFalse(loginRequest.getUsername())
                .orElseThrow(()-> new UsernameNotFoundException(MessageConstant.USER_NOT_FOUND));

        // Check status
        if (user.getStatus() != Status.ACTIVE){
            throw new AuthenException(MessageConstant.USER_IS_LOCKED,"username");
        }

        // Check password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())){
            throw new AuthenException(MessageConstant.INVALID_USER_NAME_OR_PASSWORD,"password");
        }

        return user;
    }
}
