package com.english.education.model.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
@SuppressWarnings("ClassCanBeRecord")
public class UserListProjection {
    private final Integer id;
    private final String username;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String avatar;
    private final String status;
    private final String roles;
}
