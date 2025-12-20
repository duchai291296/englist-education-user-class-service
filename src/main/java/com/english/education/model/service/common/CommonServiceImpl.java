package com.english.education.model.service.common;

import com.english.education.constant.Constants;
import org.springframework.stereotype.Service;

@Service
public class CommonServiceImpl {
    public String getTokenVerDevice(String deviceType, Integer userId) {
        if (deviceType.equals(Constants.PC)) {
            return Constants.TOKEN_VER_PC + userId;
        } else {
            return Constants.TOKEN_VER_MOBILE + userId;
        }
    }
}
