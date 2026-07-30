package com.kissanbandhu.weather.forecast.messaging;

import com.kissanbandhu.weather.common.config.RabbitMqConfig;
import com.kissanbandhu.weather.forecast.messaging.event.CacheInvalidateForecastEvent;
import com.kissanbandhu.weather.forecast.messaging.event.PersistForecastEvent;
import com.kissanbandhu.weather.forecast.messaging.event.RefreshForecastEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ForecastEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ForecastEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishRefreshRequested(RefreshForecastEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, ForecastRabbitMqConfig.RK_FORECAST_REFRESH, event);
    }

    public void publishPersistRequested(PersistForecastEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, ForecastRabbitMqConfig.RK_FORECAST_PERSIST, event);
    }

    public void publishCacheInvalidate(CacheInvalidateForecastEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, ForecastRabbitMqConfig.RK_FORECAST_CACHE_INVALIDATE, event);
    }
}
