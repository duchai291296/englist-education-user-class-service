package com.english.education.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisTestRunner implements CommandLineRunner {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) {
        //        redisTemplate.opsForValue().set("ping", "pong");
//        log.info((String) redisTemplate.opsForValue().get("token_ver:1"));
//        Object value = redisTemplate.opsForValue().get("user_locked:1");
//        log.info(value != null ? value.toString() : "null");

        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        log.info(ops.get("user_locked:1"));
    }
}

