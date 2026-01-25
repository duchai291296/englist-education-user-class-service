package com.english.education.listener;

import com.english.education.constant.Constants;
import com.english.education.event.UserCreatedEvent;
import com.english.education.model.enums.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserAuthCacheListener {
    private final StringRedisTemplate redisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {

        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        ops.set(
                Constants.USER_LOCKED_KEY + event.userId(),
                Status.ACTIVE.name()
        );
    }
}
