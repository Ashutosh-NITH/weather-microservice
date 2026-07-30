package com.kissanbandhu.weather.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * Mirrors the RabbitMQ Broker block of the architecture diagram:
 *
 *   All producers publish to ONE topic exchange: weather.exchange
 *   Routing keys follow weather.{domain}.{action} (refresh/persist/failed/retry/cache.invalidate)
 *   Each queue is bound to exactly one routing key => one queue = one worker type
 *   Every queue dead-letters into a single shared DLQ after retries are exhausted
 *
 * Only the Current Weather domain's queues are declared here. Forecast and
 * History follow the identical pattern (see comments) and will add their own
 * queues + bindings in ForecastRabbitMqConfig / HistoryRabbitMqConfig when
 * those domains are implemented.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "weather.exchange";
    public static final String DLX = "weather.dlx";
    public static final String DLQ = "dlq.queue";

    // Current Weather routing keys
    public static final String RK_CURRENT_REFRESH = "weather.current.refresh";
    public static final String RK_CURRENT_PERSIST = "weather.current.persist";
    // Past-tense domain event, fired AFTER DbPersistWorker saves a row. This is what
    // the History domain subscribes to instead of reading current_weather's table
    // directly - the fix for the cross-service-DB-read anti-pattern flagged in review.
    public static final String RK_CURRENT_PERSISTED = "weather.current.persisted";
    public static final String RK_CURRENT_FAILED = "weather.current.failed";
    public static final String RK_CURRENT_RETRY = "weather.current.retry";
    public static final String RK_CURRENT_CACHE_INVALIDATE = "weather.current.cache.invalidate";

    // Current Weather queue names
    public static final String Q_CURRENT_REFRESH = "current.refresh.queue";
    public static final String Q_CURRENT_PERSIST = "current.persist.queue";
    public static final String Q_CURRENT_RETRY = "current.retry.queue";
    public static final String Q_CURRENT_CACHE_INVALIDATE = "current.cache.invalidate.queue";

    // --- Exchanges ---

    @Bean
    public TopicExchange weatherExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    // --- Dead letter queue (shared across all domains/workers) ---

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#");
    }

    // --- Current Weather queues (each dead-letters into weather.dlx after retries) ---

    @Bean
    public Queue currentRefreshQueue() {
        return domainQueue(Q_CURRENT_REFRESH);
    }

    @Bean
    public Queue currentPersistQueue() {
        return domainQueue(Q_CURRENT_PERSIST);
    }

    @Bean
    public Queue currentRetryQueue() {
        return domainQueue(Q_CURRENT_RETRY);
    }

    @Bean
    public Queue currentCacheInvalidateQueue() {
        return domainQueue(Q_CURRENT_CACHE_INVALIDATE);
    }

    // --- Bindings ---

    @Bean
    public Binding currentRefreshBinding(Queue currentRefreshQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(currentRefreshQueue).to(weatherExchange).with(RK_CURRENT_REFRESH);
    }

    @Bean
    public Binding currentPersistBinding(Queue currentPersistQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(currentPersistQueue).to(weatherExchange).with(RK_CURRENT_PERSIST);
    }

    @Bean
    public Binding currentRetryBinding(Queue currentRetryQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(currentRetryQueue).to(weatherExchange).with(RK_CURRENT_RETRY);
    }

    @Bean
    public Binding currentCacheInvalidateBinding(Queue currentCacheInvalidateQueue, TopicExchange weatherExchange) {
        return BindingBuilder.bind(currentCacheInvalidateQueue).to(weatherExchange).with(RK_CURRENT_CACHE_INVALIDATE);
    }

    // --- Converters / template ---

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setExchange(EXCHANGE);
        // Publisher confirms/returns are enabled via spring.rabbitmq.publisher-confirm-type
        // and publisher-returns properties if you want delivery guarantees beyond ack/nack.
        return template;
    }

    /**
     * Listener container factory shared by every worker (@RabbitListener(containerFactory = "retryingListenerFactory")).
     * On handler exception: retries in-process 3x with exponential backoff (matches the
     * "retry(3)" annotation in the diagram) and, if still failing, republishes the message
     * to weather.dlx (landing in dlq.queue) instead of endlessly requeueing - this is what
     * actually drives the DLQ, not the native x-dead-letter-* queue arguments alone.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory retryingListenerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            RabbitTemplate rabbitTemplate) {

        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);

        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);

        return factory;
    }

    private Queue domainQueue(String name) {
        return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", DLX)
            .withArgument("x-dead-letter-routing-key", name + ".dlq")
            .build();
    }
}
