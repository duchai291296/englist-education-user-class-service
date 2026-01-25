package com.english.education.model.dto.response.user;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponse {
    private Integer id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatar;
    private String status;
    private String roles;
}
