package com.english.education.model.dto.request.auth;

import com.english.education.constant.MessageConstant;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewRefreshTokenRequest {
    @NotNull(message = MessageConstant.REFRESH_TOKEN_CANNOT_BE_NULL)
    private String refreshToken;
    @NotNull(message = MessageConstant.DEVICE_TYPE_CANNOT_BE_NULL)
    private String deviceType;
}
