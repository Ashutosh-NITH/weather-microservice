package com.kissanbandhu.weather.forecast.messaging;

import com.kissanbandhu.weather.common.config.RabbitMqConfig;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Forecast domain's queues/bindings on the shared weather.exchange, following
 * the exact same pattern as RabbitMqConfig's current-weather queues: one
 * queue per worker type, dead-lettering into the shared weather.dlx/dlq.queue.
 */
@Configuration
public class ForecastRabbitMqConfig {

    public static final String RK_FORECAST_REFRESH = "weather.forecast.refresh";
    public static final String RK_FORECAST_PERSIST = "weather.forecast.persist";
    public static final String RK_FORECAST_CACHE_INVALIDATE = "weather.forecast.cache.invalidate";

    public static final String Q_FORECAST_REFRESH = "forecast.refresh.queue";
    public static final String Q_FORECAST_PERSIST = "forecast.persist.queue";
    public static final String Q_FORECAST_CACHE_INVALIDATE = "forecast.cache.invalidate.queue";

    @Bean
    public Queue forecastRefreshQueue() {
        return domainQueue(Q_FORECAST_REFRESH);
    }

    @Bean
    public Queue forecastPersistQueue() {
        return domainQueue(Q_FORECAST_PERSIST);
    }

    @Bean
    public Queue forecastCacheInvalidateQueue() {
        return domainQueue(Q_FORECAST_CACHE_INVALIDATE);
    }

    @Bean
    public Binding forecastRefreshBinding(Queue forecastRefreshQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(forecastRefreshQueue).to(weatherExchange).with(RK_FORECAST_REFRESH);
    }

    @Bean
    public Binding forecastPersistBinding(Queue forecastPersistQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(forecastPersistQueue).to(weatherExchange).with(RK_FORECAST_PERSIST);
    }

    @Bean
    public Binding forecastCacheInvalidateBinding(Queue forecastCacheInvalidateQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(forecastCacheInvalidateQueue).to(weatherExchange).with(RK_FORECAST_CACHE_INVALIDATE);
    }

    private Queue domainQueue(String name) {
        return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", RabbitMqConfig.DLX)
            .withArgument("x-dead-letter-routing-key", name + ".dlq")
            .build();
    }
}
