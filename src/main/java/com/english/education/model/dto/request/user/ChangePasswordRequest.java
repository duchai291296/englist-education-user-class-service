package com.english.education.model.dto.request.user;

import com.english.education.constant.MessageConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChangePasswordRequest {

    private Integer userId;

    @NotBlank(message = MessageConstant.PASSWORD_CANNOT_BE_NULL)
    private String newPassword;

    @NotBlank(message = MessageConstant.CONFIRM_PASSWORD_CANNOT_BE_NULL)
    private String confirmPassword;

    private String oldPassword;
}
