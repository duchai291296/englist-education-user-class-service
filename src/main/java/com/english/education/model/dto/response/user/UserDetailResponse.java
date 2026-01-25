package com.english.education.model.dto.response.user;

import com.english.education.model.enums.RoleName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserDetailResponse {
    private Integer id;
    private String userName;
    private String fullName;
    private String email;
    private String phone;
    private String avatar;
    private Set<RoleName> roles;
}
