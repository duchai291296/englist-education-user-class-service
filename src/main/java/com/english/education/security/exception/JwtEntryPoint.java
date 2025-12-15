package com.english.education.security.exception;

import com.english.education.model.dto.response.DataError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.IOException;

@Component
@Slf4j
public class JwtEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(@NonNull HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        DataError<String> dataError = new DataError<>();
        dataError.setMessage(authException.getMessage());
        dataError.setStatus(HttpStatus.UNAUTHORIZED);
        dataError.setStatusCode(401);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonResponse = objectMapper.writeValueAsString(dataError);
        response.getWriter().write(jsonResponse);
    }
}
