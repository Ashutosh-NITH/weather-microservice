package com.kissanbandhu.weather.history.messaging;

import com.kissanbandhu.weather.common.config.RabbitMqConfig;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HistoryRabbitMqConfig {

    public static final String RK_HISTORY_MONTH_SUMMARY = "weather.history.month.summary";

    public static final String Q_HISTORY_PERSIST = "history.persist.queue";
    public static final String Q_HISTORY_MONTH_SUMMARY = "history.month.summary.queue";

    /**
     * Binds to the SAME routing key current.DbPersistWorker uses for its
     * "persisted" domain event (RabbitMqConfig.RK_CURRENT_PERSISTED). This is
     * the RabbitMQ broker's "add a new consumer without changing producers"
     * benefit in action - History never had to coordinate with the Current
     * Weather domain to start listening.
     */
    @Bean
    public Queue historyPersistQueue() {
        return domainQueue(Q_HISTORY_PERSIST);
    }

    @Bean
    public Binding historyPersistBinding(Queue historyPersistQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(historyPersistQueue).to(weatherExchange).with(RabbitMqConfig.RK_CURRENT_PERSISTED);
    }

    @Bean
    public Queue historyMonthSummaryQueue() {
        return domainQueue(Q_HISTORY_MONTH_SUMMARY);
    }

    @Bean
    public Binding historyMonthSummaryBinding(Queue historyMonthSummaryQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(historyMonthSummaryQueue).to(weatherExchange).with(RK_HISTORY_MONTH_SUMMARY);
    }

    private Queue domainQueue(String name) {
        return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", RabbitMqConfig.DLX)
            .withArgument("x-dead-letter-routing-key", name + ".dlq")
            .build();
    }
}
