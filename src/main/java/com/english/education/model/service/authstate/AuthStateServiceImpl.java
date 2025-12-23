package com.english.education.model.service.authstate;

import com.english.education.constant.MessageConstant;
import com.english.education.exception.AuthenException;
import com.english.education.model.dto.response.ValidRequestResponse;
import com.english.education.model.enums.Status;
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
     * This method is READ-ONLY with respect to Redis state.
     * It does not rebuild, mutate, or initialize Redis data.
     * <p>
     * If Redis data is missing or inconsistent, the request fails fast
     * to prevent incorrect authentication state.
     * <p>
     * Circuit Breaker:
     * - Wrapped with @CircuitBreaker(name = "redisAuth", fallbackMethod = "redisAuthFallback")
     * - If Redis access throws runtime exceptions (connection failure, timeout),
     *   the circuit breaker triggers the fallback method.
     *
     * @param validRequestResponse dto have info of redis
     * @throws AuthenException if auth state is invalid or user is locked or cannot connect to redis
     * @author Duc Hai (17/12/2025)
     */
    @CircuitBreaker(
            name = "redisAuth",
            fallbackMethod = "redisAuthFallback"
    )
    @Override
    public void validateAuthState(ValidRequestResponse validRequestResponse) throws AuthenException {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        try {
            String tokenVerValue = ops.get(validRequestResponse.getTokenVerKey());
            String locked = ops.get(validRequestResponse.getLockedKey());

            Long tokenVer = parseLongOrThrow(tokenVerValue);

            // Redis cache not ready or just restarted
            if (tokenVer == null || locked == null) {
                log.warn("Auth cache missing for userId={}", validRequestResponse.getUserId());
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

            if(!tokenVer.equals(validRequestResponse.getTokenVer())){
                throw new AuthenException(
                        MessageConstant.BACK_TO_LOGIN,
                        "system"
                );
            }

            log.debug("Auth cache valid for userId={} tokenVer={}", validRequestResponse.getUserId(), tokenVer);
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
     * @param ex          Throwable that triggered fallback
     * @throws AuthenException indicating system temporarily unavailable
     */
    @SuppressWarnings("unused")
    private void redisAuthFallback(
            ValidRequestResponse validRequestResponse,
            Throwable ex
    ) throws AuthenException {
        log.error(
                "Circuit Breaker OPEN for Redis (userId={})",
                validRequestResponse.getUserId(),
                ex
        );

        throw new AuthenException(
                MessageConstant.SYSTEM_TEMPORARILY_UNAVAILABLE,
                "system"
        );
    }

    /**
     * Parse token version from Redis.
     * <p>
     * - Returns {@code null} if value is missing
     * - Throws exception if value is invalid
     * <p>
     * Invalid format indicates corrupted Redis state
     * and must be rejected immediately.
     *
     * @param value token version string from Redis
     * @return parsed token version or {@code null}
     * @throws AuthenException if value is not a valid number
     * @author Duc Hai (17/12/2025)
     */
    private Long parseLongOrThrow(String value) {
        try {
            return value != null ? Long.valueOf(value) : null;
        } catch (NumberFormatException e) {
            throw new AuthenException(
                    MessageConstant.INVALID_TOKEN_VER,
                    "system"
            );
        }
    }
}
