package com.english.education.model.dto.request.user;

import com.english.education.constant.MessageConstant;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ChangeAvatarRequest {
    @NotNull(message = MessageConstant.USER_ID_CANNOT_BE_NULL)
    private Integer userId;

    @NotNull(message = MessageConstant.IS_DELETE_CANNOT_BE_NULL)
    boolean isDelete;

    private MultipartFile avatar;
}
