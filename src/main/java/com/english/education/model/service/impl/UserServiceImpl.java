package com.english.education.model.service.impl;

import com.english.education.constant.MessageConstant;
import com.english.education.model.entity.User;
import com.english.education.model.repository.UserRepository;
import com.english.education.model.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User findById(Integer id) {
        return userRepository.findById(id).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new NoSuchElementException(MessageConstant.USER_NOT_FOUND));
    }
}
