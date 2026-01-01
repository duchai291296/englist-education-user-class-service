package com.english.education.model.service.users;

import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.user.UpdateUserRequest;
import com.english.education.model.entity.User;
import com.english.education.security.principle.UserDetailCustom;
import org.springframework.http.ResponseEntity;


public interface UserService {
    User findById(Integer id);

    ResponseEntity<?> lockUser(Integer userId);

    ResponseEntity<?> unlockUser(Integer userId);

    boolean existsByUsername(String username);

    User findByUsername(String username);

    ResponseEntity<?> detail(Integer userId);

    ResponseEntity<?> updateUser(UpdateUserRequest request, UserDetailCustom userDetailCustom) throws CustomException;
}
