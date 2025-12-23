package com.english.education.listener;

import com.english.education.event.UserLockEvent;
import com.english.education.model.enums.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserLockCacheListener {
    private final StringRedisTemplate stringRedisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserLocked(UserLockEvent event) {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        ops.set(
                event.lockedKey(),
                Status.INACTIVE.name()
        );

        stringRedisTemplate.opsForValue().increment(event.tokenVerKeyPC());
        stringRedisTemplate.opsForValue().increment(event.tokenVerKeyMobile());
    }
}
