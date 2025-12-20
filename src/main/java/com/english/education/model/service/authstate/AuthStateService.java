package com.english.education.model.service.authstate;

import com.english.education.exception.AuthenException;
import com.english.education.model.dto.response.ValidRequestResponse;
import com.english.education.model.entity.User;

public interface AuthStateService {
    void validateAuthState(ValidRequestResponse validRequestResponse) throws AuthenException;
}
