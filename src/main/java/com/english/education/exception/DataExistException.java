package com.english.education.exception;

import lombok.Getter;

@Getter
public class DataExistException extends RuntimeException {
    private final String field;
    public DataExistException(String message, String field) {
        super(message);
        this.field = field;
    }
}
