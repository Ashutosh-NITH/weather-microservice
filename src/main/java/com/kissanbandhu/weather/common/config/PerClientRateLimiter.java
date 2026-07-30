package com.kissanbandhu.weather.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PerClientRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;
    private final Duration window;

    public PerClientRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${weather.rate-limiter.per-client.requests:1}") int maxRequests,
            @Value("${weather.rate-limiter.per-client.period-minutes:15}") long windowMinutes) {

        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    public boolean tryAcquire(String clientKey, String endpoint) {

        // Make endpoint Redis-key safe
        String endpointKey = endpoint.replace("/", ":");

        String redisKey = "ratelimit:client:" + clientKey + ":" + endpointKey;

        Long count = redisTemplate.opsForValue().increment(redisKey);

        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }

        return count != null && count <= maxRequests;
    }
}