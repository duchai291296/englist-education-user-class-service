package com.english.education.exception;

import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

@Getter
public class AuthenException extends AuthenticationException {
    private final String field;
    public AuthenException(String message, String field) {
        super(message);
        this.field = field;
    }
}
