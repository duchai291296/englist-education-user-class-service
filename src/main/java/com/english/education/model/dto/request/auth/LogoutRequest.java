package com.english.education.model.dto.request.auth;

import com.english.education.constant.MessageConstant;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LogoutRequest {
    @NotNull(message = MessageConstant.DEVICE_TYPE_CANNOT_BE_NULL)
    private String deviceType;
}
