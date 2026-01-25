package com.english.education.model.repository.user;

import com.english.education.exception.CustomException;
import com.english.education.model.dto.response.user.UserListResponse;
import com.english.education.model.enums.RoleName;
import com.english.education.model.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface UserRepositoryCustom {
    Page<UserListResponse> searchAll(
            String search,
            Status status,
            Set<RoleName> roles,
            Pageable pageable
    ) throws CustomException;
}
