package com.english.education.model.repository.user;

import com.english.education.exception.CustomException;
import com.english.education.model.dto.response.user.UserListProjection;
import com.english.education.model.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface UserRepositoryCustom {
    Page<UserListProjection> searchAll(
            String search,
            String status,
            Set<RoleName> roles,
            Pageable pageable
    ) throws CustomException;
}
