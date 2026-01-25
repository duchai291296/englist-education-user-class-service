package com.english.education.advice;

import com.english.education.exception.AuthedException;
import com.english.education.exception.CustomException;
import com.english.education.exception.DataExistException;
import com.english.education.model.dto.response.DataError;
import com.english.education.model.enums.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

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

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<DataError<String>> handleCustomException(CustomException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(new DataError<>(
                        ex.getMessage(),
                        ex.getStatus(),
                        ex.getStatus().value()
                ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<DataError<String>> handleNoSuchElementException(NoSuchElementException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new DataError<>(
                        ex.getMessage(),
                        HttpStatus.NOT_FOUND,
                        HttpStatus.NOT_FOUND.value()
                ));
    }

    @ExceptionHandler(AuthedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public DataError<Map<String,String>> handleErrorDataExist(AuthedException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getField(), ex.getMessage());
        return new DataError<>(errors,HttpStatus.BAD_REQUEST,HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<DataError<String>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {

        Class<?> requiredType = ex.getRequiredType();
        if (requiredType == Status.class) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new DataError<>(
                        "Invalid status value",
                        HttpStatus.BAD_REQUEST,
                        HttpStatus.BAD_REQUEST.value()
                ));
        }

        if ("roles".equals(ex.getName())) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new DataError<>(
                    "Invalid role value",
                    HttpStatus.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new DataError<>("Invalid request parameter", HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value()));
    }


}
