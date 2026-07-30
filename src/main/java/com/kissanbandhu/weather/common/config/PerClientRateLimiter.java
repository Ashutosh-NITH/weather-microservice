package com.kissanbandhu.weather.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Distributed (works across all horizontally-scaled instances) fixed-window
 * rate limiter: INCR key, and only set an expiry the first time the key is
 * created within the window. Backs the "Rate Limiter: 1 request per 15 min"
 * box in the diagram, keyed per authenticated client rather than per pod.
 */
@Component
public class PerClientRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final int maxRequests;
    private final Duration window;

    public PerClientRateLimiter(StringRedisTemplate redisTemplate,
                                 @Value("${weather.rate-limiter.per-client.requests:1}") int maxRequests,
                                 @Value("${weather.rate-limiter.per-client.period-minutes:15}") long windowMinutes) {
        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /**
     * Returns true if the call is allowed (and records it), false if the
     * client is over budget for the current window.
     */
    public boolean tryAcquire(String clientKey) {
        String redisKey = "ratelimit:client:" + clientKey;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count != null && count <= maxRequests;
    }
}
