package com.english.education.model.service;

import com.english.education.exception.AuthenException;
import com.english.education.model.entity.User;

public interface AuthStateService {
    String validateAuthState(String tokenVerKey, String lockedKey, User user) throws AuthenException;
}
