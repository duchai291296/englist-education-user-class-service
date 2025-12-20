package com.english.education.model.service.users;

import com.english.education.model.entity.User;


public interface UserService {
    User findById(Integer id);

    boolean existsByUsername(String username);

    User findByUsername(String username);
}
