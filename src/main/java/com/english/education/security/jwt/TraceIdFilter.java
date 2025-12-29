package com.english.education.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static com.english.education.constant.Constants.TRACE_ID;
import static com.english.education.constant.Constants.TRACE_ID_HEADER;

/**
 * HTTP request filter responsible for traceId propagation and logging correlation.
 * <p>
 * Flow:
 * 1. Read traceId from incoming request header (if provided by client or gateway)
 * 2. Generate a new traceId if the header is missing or blank
 * 3. Bind traceId to MDC for log correlation within the current request thread
 * 4. Propagate traceId back to client via response header
 * 5. Ensure MDC cleanup after request completion
 * <p>
 * Responsibilities:
 * - Guarantee every request has a traceId
 * - Enable log correlation across layers (filter → controller → service)
 * - Support distributed tracing across services when traceId is forwarded
 * <p>
 * MDC lifecycle:
 * - traceId is stored in ThreadLocal MDC for the duration of the request
 * - traceId is removed in finally block to prevent leakage across reused threads
 * <p>
 * Notes:
 * - This filter must execute before authentication and business logic filters
 * - Only traceId key is removed from MDC (no global MDC.clear)
 * - Designed to be safe for multithreaded servlet environments
 *
 * @author Duc Hai
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Prevent MDC leakage when the servlet thread is reused
            MDC.remove(TRACE_ID);
        }
    }
}
