package com.kissanbandhu.weather.common.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed distributed lock: SET lock:{key} {token} NX EX {ttl}.
 *
 * Deliberately per-holder-token, not a static "true" value: releasing the
 * lock is a compare-and-delete (Lua script, so it's atomic) that only
 * succeeds if the caller still holds the token it was given at acquire()
 * time. Without this, a slow worker whose TTL already expired could delete
 * a lock acquired by a *different* worker in the meantime - the exact bug
 * this class was rewritten to avoid.
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private static final String RELEASE_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";

    private final StringRedisTemplate redisTemplate;
    private final long defaultTtlSeconds;
    private final DefaultRedisScript<Long> releaseScript;

    public DistributedLockService(StringRedisTemplate redisTemplate,
                                   @Value("${weather.lock.ttl-seconds:10}") long defaultTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
    }

    /**
     * Attempts to acquire the lock. Returns the holder token to pass back into
     * release() if successful, or null if someone else already holds it.
     */
    public String tryLock(String key) {
        return tryLock(key, Duration.ofSeconds(defaultTtlSeconds));
    }

    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        String redisKey = lockKey(key);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return token;
        }
        return null;
    }

    /**
     * Releases the lock only if the given token still matches what's in Redis.
     */
    public boolean release(String key, String token) {
        if (token == null) {
            return false;
        }
        String redisKey = lockKey(key);
        Long result = redisTemplate.execute(releaseScript, List.of(redisKey), token);
        boolean released = result != null && result == 1L;
        if (!released) {
            log.warn("Lock release skipped for key={} - token mismatch or already expired", key);
        }
        return released;
    }

    private String lockKey(String key) {
        return "lock:" + key;
    }
}
