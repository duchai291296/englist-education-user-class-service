package com.english.education.advice;

import com.english.education.exception.DataExistException;
import com.english.education.model.dto.response.DataError;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApplicationHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public DataError<Map<String,String>> handleError(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getFieldErrors().forEach(err->errors.put(err.getField(), err.getDefaultMessage()));
        return new DataError<>(errors,HttpStatus.BAD_REQUEST,HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(DataExistException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public DataError<Map<String,String>> handleErrorDataExist(DataExistException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getField(), ex.getMessage());
        return new DataError<>(errors,HttpStatus.BAD_REQUEST,HttpStatus.BAD_REQUEST.value());

    }
}
