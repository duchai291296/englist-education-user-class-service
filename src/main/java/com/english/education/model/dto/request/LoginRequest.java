package com.english.education.model.dto.request;

import com.english.education.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LoginRequest {
    @NotBlank(message = MessageConstant.USER_NAME_CANNOT_BE_NULL)
    private String username;
    @NotBlank(message = MessageConstant.PASSWORD_CANNOT_BE_NULL)
    private String password;
}
