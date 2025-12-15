package com.english.education.security.exception;

import com.english.education.model.dto.response.DataError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@Slf4j
public class AccessDenied implements AccessDeniedHandler {
    @Override
    public void handle(@NonNull HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        DataError<String> dataError = new DataError<>();
        dataError.setMessage(accessDeniedException.getMessage());
        dataError.setStatus(HttpStatus.FORBIDDEN);
        dataError.setStatusCode(403);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonResponse = objectMapper.writeValueAsString(dataError);
        response.getWriter().write(jsonResponse);
    }
}
