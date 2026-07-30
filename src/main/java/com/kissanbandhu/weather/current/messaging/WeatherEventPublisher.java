package com.kissanbandhu.weather.current.messaging;

import com.kissanbandhu.weather.common.config.RabbitMqConfig;
import com.kissanbandhu.weather.current.messaging.event.CacheInvalidateEvent;
import com.kissanbandhu.weather.current.messaging.event.PersistWeatherEvent;
import com.kissanbandhu.weather.current.messaging.event.RefreshWeatherEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class WeatherEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public WeatherEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishRefreshRequested(RefreshWeatherEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.RK_CURRENT_REFRESH, event);
    }

    public void publishPersistRequested(PersistWeatherEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.RK_CURRENT_PERSIST, event);
    }

    /**
     * Fired by DbPersistWorker AFTER a successful save. The History domain
     * binds its own queue to this routing key to build up weather_history -
     * it never touches current_weather's table directly.
     */
    public void publishPersisted(PersistWeatherEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.RK_CURRENT_PERSISTED, event);
    }

    public void publishCacheInvalidate(CacheInvalidateEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.RK_CURRENT_CACHE_INVALIDATE, event);
    }
}
