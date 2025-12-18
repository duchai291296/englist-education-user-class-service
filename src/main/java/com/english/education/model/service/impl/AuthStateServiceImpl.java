package com.english.education.model.service.impl;

import com.english.education.constant.MessageConstant;
import com.english.education.exception.AuthenException;
import com.english.education.model.entity.User;
import com.english.education.model.enums.Status;
import com.english.education.model.service.AuthStateService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthStateServiceImpl implements AuthStateService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Validate authentication state from Redis cache.
     * <p>
     * Rules:
     * - Both token version and lock status must exist
     * - User must not be locked
     * <p>
     * This method is READ-ONLY.
     * It never rebuilds or modifies Redis state.
     * <p>
     * If Redis data is missing or inconsistent, the request fails fast
     * to prevent incorrect authentication state.
     * <p>
     * Circuit Breaker:
     * - Wrapped with @CircuitBreaker(name = "redisAuth", fallbackMethod = "redisAuthFallback")
     * - If Redis is unreachable or takes too long, the fallback method is called
     *   to prevent system-wide failure.
     *
     * @param tokenVerKey Redis key storing token version
     * @param lockedKey   Redis key storing user lock status
     * @param user        authenticated user
     * @return token version
     * @throws AuthenException if auth state is invalid or user is locked or cannot connect to redis
     * @author Duc Hai (17/12/2025)
     */
    @CircuitBreaker(
            name = "redisAuth",
            fallbackMethod = "redisAuthFallback"
    )
    @Override
    public String validateAuthState(String tokenVerKey, String lockedKey, User user) throws AuthenException {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        try {
            String tokenVer = ops.get(tokenVerKey);
            String locked = ops.get(lockedKey);

            // Redis cache not ready or just restarted
            if (tokenVer == null || locked == null) {
                log.warn("Auth cache missing for userId={}", user.getId());
                throw new AuthenException(
                        MessageConstant.AUTH_TRY_AGAIN_LATER,
                        "system"
                );
            }

            // User has been locked by admin
            if (Status.INACTIVE.name().equals(locked)) {
                throw new AuthenException(
                        MessageConstant.USER_IS_LOCKED,
                        "username"
                );
            }
            log.debug("Auth cache valid for userId={} tokenVer={}", user.getId(), tokenVer);

            return tokenVer;
        } catch (RedisConnectionFailureException e) {
            log.error("Redis UNREACHABLE - cannot connect", e);
            throw new AuthenException(
                    MessageConstant.SYSTEM_TEMPORARILY_UNAVAILABLE,
                    "system"
            );
        }
    }

    /**
     * Fallback method for circuit breaker when Redis validation fails.
     * <p>
     * This method is invoked automatically if:
     * - Redis is unreachable
     * - Redis request times out
     * <p>
     * It always throws AuthenException to indicate temporary system unavailability.
     * Logging helps monitor circuit breaker activation.
     *
     * @param tokenVerKey Redis key
     * @param lockedKey   Redis key
     * @param user        User entity
     * @param ex          Throwable that triggered fallback
     * @return never returns normally, always throws AuthenException
     * @throws AuthenException indicating system temporarily unavailable
     */
    @SuppressWarnings("unused")
    private String redisAuthFallback(
            String tokenVerKey,
            String lockedKey,
            User user,
            Throwable ex
    ) throws AuthenException {
        log.error(
                "Circuit Breaker OPEN for Redis (userId={})",
                user.getId(),
                ex
        );

        throw new AuthenException(
                MessageConstant.SYSTEM_TEMPORARILY_UNAVAILABLE,
                "system"
        );
    }
}
