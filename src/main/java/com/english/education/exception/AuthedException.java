package com.english.education.exception;

import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

@Getter
public class AuthedException extends AuthenticationException {
    private final String field;
    public AuthedException(String message, String field) {
        super(message);
        this.field = field;
    }
}
