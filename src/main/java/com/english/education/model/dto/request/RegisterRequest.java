package com.english.education.model.dto.request;

import com.english.education.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank(message = MessageConstant.USER_NAME_CANNOT_BE_NULL)
    private String username;
    @NotBlank(message = MessageConstant.PASSWORD_CANNOT_BE_NULL)
    private String password;
    private String email;
    private String phone;
    @NotBlank(message = MessageConstant.FULL_NAME_CANNOT_BE_NULL)
    private String fullName;
    @NotNull(message = MessageConstant.ROLE_CANNOT_BE_NULL)
    @NotEmpty(message = MessageConstant.ROLE_CANNOT_BE_EMPTY)
    private Set<String> roles;
}
