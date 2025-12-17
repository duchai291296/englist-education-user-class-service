package com.english.education.model.dto.response;

import com.english.education.model.enums.RoleName;
import com.english.education.model.enums.Status;
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
    private String refreshToken;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private Status status;
    private Set<RoleName> roles;
}
