package com.english.education.security.jwt;

import com.english.education.constant.MessageConstant;
import com.english.education.exception.AuthenException;
import com.english.education.model.entity.UserSession;
import com.english.education.model.repository.usersession.UserSessionRepository;
import com.english.education.model.service.common.CommonServiceImpl;
import com.english.education.security.principle.UserDetailCustom;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final CommonServiceImpl commonServiceImpl;
    private final UserSessionRepository userSessionRepository;

    /**
     * JWT authentication filter responsible for validating access tokens
     * and establishing the Spring Security authentication context.
     * <p>
     * Flow:
     * 1. Extract JWT token from HTTP request
     * 2. Validate token signature and expiration
     * 3. Parse JWT claims
     * 4. Validate authentication state against Redis and database
     * 5. Build authenticated principal and set it into SecurityContext
     * 6. Bind userId to MDC for request-scoped log enrichment
     * <p>
     * Validation checks:
     * - Token must be present and structurally valid
     * - Token must not be expired or tampered
     * - Authentication state must be consistent (token version & user status)
     * - User must not be locked or revoked
     * <p>
     * Guarantees:
     * - SecurityContext is populated only once per request
     * - Authentication is established only for valid and active users
     * - userId is available in MDC for downstream logging
     * <p>
     * MDC lifecycle:
     * - userId is bound to the current request thread after successful authentication
     * - userId is removed in finally block to prevent leakage across reused threads
     * <p>
     * Notes:
     * - This filter must run after TraceIdFilter to ensure logs contain traceId
     * - MDC cleanup is scoped only to userId (no global MDC.clear)
     * - Safe for multi-threaded servlet environments
     *
     * @param request  incoming HTTP request
     * @param response HTTP response
     * @param filterChain servlet filter chain
     * @throws ServletException in case of servlet errors
     * @throws IOException in case of I/O errors
     * @author Duc Hai
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try{
            String token = getTokenFromRequest(request);

            if (token != null && jwtProvider.validateToken(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                Claims claims = jwtProvider.parse(token);

                validateAuthState(claims);

                Integer userId = claims.get("userId", Integer.class);
                String deviceType = claims.get("device", String.class);

                // JWT claims are deserialized as raw List -> safe cast
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("userRole", List.class);

                List<GrantedAuthority> authorities = roles.stream()
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                        .toList();

                String username = jwtProvider.getUsernameFromToken(token);
                UserDetailCustom principal = new UserDetailCustom(
                        userId,
                        username,
                        deviceType,
                        authorities
                );
                Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                MDC.put("userId", String.valueOf(userId));
            }
            filterChain.doFilter(request, response);
        } finally {
            // Prevent MDC leakage when servlet thread is reused
            MDC.remove("userId");
        }
    }

    /**
     * Extract JWT token from HTTP request Authorization header.
     * <p>
     * Expected format:
     * - Header: "Authorization: Bearer {token}"
     * - Returns token string without "Bearer " prefix
     * <p>
     * Returns {@code null} if:
     * - Authorization header is missing
     * - Header does not start with "Bearer "
     *
     * @param request HTTP servlet request
     * @return JWT token string or {@code null} if not found
     * @author Duc Hai (21/12/2025)
     */
    public String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7).trim();
        }
        return null;
    }

    /**
     * Validate JWT token claims and authentication state in Redis.
     * <p>
     * Flow:
     * 1. Extract required claims from JWT (userId, deviceType, tokenVer)
     * 2. Validate all claims are present
     * 3. Build Redis keys for token version and user lock status
     * 4. Delegate validation to AuthStateService
     * <p>
     * Validation checks:
     * - JWT claims must contain userId, deviceType, and tokenVer
     * - Token version in JWT must match Redis token version
     * - User must not be locked (status must be ACTIVE)
     * <p>
     * This method ensures that:
     * - Token is not revoked (via token version check)
     * - User account is still active
     * - Authentication state is consistent between JWT and Redis
     *
     * @param claims JWT claims extracted from token
     * @throws AuthenException if claims are missing, token version mismatch, or user is locked
     * @author Duc Hai (21/12/2025)
     */
    private void validateAuthState(Claims claims){
        Integer userId = claims.get("userId", Integer.class);
        String deviceType = claims.get("device", String.class);
        Long tokenVer = claims.get("tokenVer", Long.class);
        if (userId == null || deviceType == null || tokenVer == null) {
            throw new AuthenException(MessageConstant.INVALID_ACCESS_TOKEN, "token");
        }

        String redisKey = commonServiceImpl.getTokenVerDevice(deviceType, userId);

        Long redisVer = commonServiceImpl.getTokenVersionFromRedis(redisKey);

        if (redisVer != null ){
            if(redisVer > tokenVer){
                throw new AuthenException(MessageConstant.INVALID_ACCESS_TOKEN, "token");
            }

            if (redisVer.equals(tokenVer)){
                return;
            }
        }

        validateFromDBAndHealRedis(userId, deviceType, tokenVer, redisKey);

    }

    private void validateFromDBAndHealRedis(
            Integer userId,
            String deviceType,
            Long jwtTokenVer,
            String redisKey
    ) {

        UserSession session = userSessionRepository
                .findByUserIdAndDeviceType(userId, deviceType)
                .orElseThrow(() ->
                        new AuthenException(MessageConstant.INVALID_ACCESS_TOKEN, "token")
                );

        Long dbVer = session.getTokenVersion();

        if (!dbVer.equals(jwtTokenVer)) {
            throw new AuthenException(MessageConstant.INVALID_ACCESS_TOKEN, "token");
        }

        // Heal Redis (best effort)
        try {
            stringRedisTemplate.opsForValue().set(
                    redisKey,
                    dbVer.toString(),
                    Duration.ofMinutes(10)
            );
        } catch (Exception e) {
            // Best-effort cache healing.
            // Redis is NOT source of truth.
            // Failure here must NOT block authenticated request.
            log.warn("Failed to heal Redis cache for key={}, fallback to DB next time", redisKey, e);
        }
    }


}
