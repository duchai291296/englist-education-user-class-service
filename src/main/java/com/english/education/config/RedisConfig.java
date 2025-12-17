package com.english.education.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // Create RedisTemplate with String keys and Object values
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // Set Redis connection factory
        template.setConnectionFactory(connectionFactory);

        // Configure serializer for String keys
        template.setKeySerializer(new StringRedisSerializer());

        // Configure serializer for String hash keys
        template.setHashKeySerializer(new StringRedisSerializer());

        // Configure serializer for JSON values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        // Configure serializer for JSON hash values
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        // Finalize RedisTemplate configuration
        template.afterPropertiesSet();

        return template;
    }
}

