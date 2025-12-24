package com.english.education.model.service.users;

import com.english.education.model.entity.User;
import org.springframework.http.ResponseEntity;


public interface UserService {
    User findById(Integer id);

    ResponseEntity<?> lockUser(Integer userId);

    ResponseEntity<?> unlockUser(Integer userId);

    boolean existsByUsername(String username);

    User findByUsername(String username);
}
