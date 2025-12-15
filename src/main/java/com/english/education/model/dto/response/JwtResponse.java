package com.english.education.model.dto.response;

import lombok.*;

import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class JwtResponse {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String status;
    private Set<String> roles;
}
