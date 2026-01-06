package com.english.education.model.dto.response.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserListResponse {
    private Integer id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String status;
    private String avatar;
    private String roles;
}
