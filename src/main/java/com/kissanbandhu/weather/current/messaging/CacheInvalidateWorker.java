package com.kissanbandhu.weather.current.messaging;

import com.kissanbandhu.weather.current.messaging.event.CacheInvalidateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component("currentCacheInvalidateWorker")
public class CacheInvalidateWorker {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidateWorker.class);
    private static final String CACHE_PREFIX = "current:weather:";

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheInvalidateWorker(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = "current.cache.invalidate.queue", containerFactory = "retryingListenerFactory")
    public void onCacheInvalidateRequested(CacheInvalidateEvent event) {
        log.debug("Invalidating Redis cache for geohash={}", event.geohash());
        redisTemplate.delete(CACHE_PREFIX + event.geohash());
    }
}
