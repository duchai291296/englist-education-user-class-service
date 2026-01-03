package com.english.education.model.service.authstate;

import com.english.education.exception.AuthedException;
import com.english.education.model.dto.response.ValidRequestResponse;

public interface AuthStateService {
    void validateAuthState(ValidRequestResponse validRequestResponse) throws AuthedException;
}
