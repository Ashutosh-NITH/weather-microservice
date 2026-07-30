package com.kissanbandhu.weather.history.messaging;

import com.kissanbandhu.weather.common.config.RabbitMqConfig;
import com.kissanbandhu.weather.history.messaging.event.RefreshMonthSummaryEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class HistoryEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public HistoryEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishRefreshMonthSummary(RefreshMonthSummaryEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, HistoryRabbitMqConfig.RK_HISTORY_MONTH_SUMMARY, event);
    }
}
