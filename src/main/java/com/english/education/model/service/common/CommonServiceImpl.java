package com.english.education.model.service.common;

import com.english.education.constant.Constants;
import com.english.education.model.enums.RoleName;
import com.english.education.security.principle.UserDetailCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommonServiceImpl implements CommonService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Build Redis key for token version by device type.
     * <p>
     * Each device maintains its own token version to allow
     * independent session invalidation (PC vs Mobile).
     *
     * @param deviceType device type (PC or MOBILE)
     * @param userId     user identifier
     * @return Redis key for token version
     * @author Duc Hai (17/12/2025)
     */
    @Override
    public String getTokenVerDevice(String deviceType, Integer userId) {
        if (deviceType.equals(Constants.PC)) {
            return Constants.TOKEN_VER_PC + userId;
        } else {
            return Constants.TOKEN_VER_MOBILE + userId;
        }
    }

    @Override
    public Long getTokenVersionFromRedis(String key) {
        try {
            String val = stringRedisTemplate.opsForValue().get(key);
            return val != null ? Long.parseLong(val) : null;
        } catch (Exception e) {
            log.warn("Redis unavailable, fallback DB");
            return null;
        }
    }

    @Override
    public String getFileName(MultipartFile file) {
        if (file == null) {
            return null;
        }

        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return null;
        }

        int dotIndex = original.lastIndexOf('.');
        if (dotIndex > 0) {
            return original.substring(0, dotIndex);
        }

        return original;
    }

    @Override
    public boolean isAdmin(UserDetailCustom userDetailCustom) {
        return userDetailCustom.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleName.ADMIN.name()));
    }
}
