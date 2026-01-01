package com.english.education.model.dto.request.user;

import com.english.education.constant.MessageConstant;
import com.english.education.model.enums.RoleName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UpdateUserRequest {
    @NotNull(message = MessageConstant.USER_ID_CANNOT_BE_NULL)
    private Integer userUpdateId;
    @NotBlank(message = MessageConstant.FULL_NAME_CANNOT_BE_NULL)
    private String fullName;
    private String email;
    private String phone;
    private Set<RoleName> roles;
}
