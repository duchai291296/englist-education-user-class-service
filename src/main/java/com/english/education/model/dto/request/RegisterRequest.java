package com.english.education.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String phone;
    private String fullName;
    private Set<String> roles;
}
