package com.english.education.model.dto.request.user;

import com.english.education.constant.MessageConstant;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LockAndUnlockRequest {
    @NotNull(message = MessageConstant.USER_ID_CANNOT_BE_NULL)
    private Integer userId;
}
