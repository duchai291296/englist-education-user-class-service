package com.english.education.exception;

import lombok.Getter;

@Getter
public class AuthenException extends Exception {
    private final String field;
    public AuthenException(String message, String field) {
        super(message);
        this.field = field;
    }
}
