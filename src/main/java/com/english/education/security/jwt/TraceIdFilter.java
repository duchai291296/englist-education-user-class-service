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
 * HTTP request filter responsible for traceId propagation and log correlation.
 * <p>
 * Execution flow:
 * <pre>
 * 1. Attempt to read traceId from the incoming request header (forwarded by client or gateway)
 * 2. Generate a new traceId if the header is missing or blank
 * 3. Bind traceId to MDC for log correlation within the current request thread
 * 4. Propagate traceId back to the client via response header when it was locally generated
 * 5. Ensure MDC cleanup after request completion
 * </pre>
 * Responsibilities:
 * <pre>
 * - Guarantee that every request processed by this service has a traceId
 * - Enable log correlation across application layers (filter → controller → service)
 * - Support distributed tracing when traceId is propagated across services
 * </pre>
 * MDC lifecycle:
 * <pre>
 * - traceId is stored in ThreadLocal MDC for the duration of the request
 * - traceId is removed in the finally block to prevent leakage across reused servlet threads
 * </pre>
 * Notes:
 * <pre>
 * - This filter should execute before authentication and business logic filters
 * - Response header is only set when traceId is generated locally
 * - Only the traceId key is removed from MDC (no global MDC.clear)
 * - Designed to be safe for multithreaded servlet environments
 *</pre>
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

        // Attempt to retrieve traceId from incoming request header
        // This is expected when the request is forwarded by an API Gateway or upstream service
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // Generate a new traceId when none is provided
        // This ensures standalone or bypassed requests are still traceable
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();

            // Propagate the newly generated traceId back to the client
            // Only set the header if it has not already been written
            if(!response.containsHeader(TRACE_ID_HEADER)) {
                response.setHeader(TRACE_ID_HEADER, traceId);
            }
        }

        // Bind traceId to MDC so all logs within this request share the same correlation id
        MDC.put(TRACE_ID, traceId);

        try {
            // Continue filter chain execution with traceId bound to the current thread
            filterChain.doFilter(request, response);
        } finally {
            // Remove traceId from MDC to prevent thread-local leakage
            // This is critical because servlet threads are reused by the container
            MDC.remove(TRACE_ID);
        }
    }
}
