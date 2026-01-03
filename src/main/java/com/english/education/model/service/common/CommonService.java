package com.english.education.model.service.common;

import com.english.education.security.principle.UserDetailCustom;
import org.springframework.web.multipart.MultipartFile;

public interface CommonService {
    String getTokenVerDevice(String deviceType, Integer userId);
    Long getTokenVersionFromRedis(String key);
    String getFileName(MultipartFile file);
    boolean isAdmin(UserDetailCustom userDetailCustom);
}
