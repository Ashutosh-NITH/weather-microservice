package com.kissanbandhu.weather.history.messaging;

import com.kissanbandhu.weather.current.dto.CurrentWeatherResponseDto;
import com.kissanbandhu.weather.current.messaging.event.PersistWeatherEvent;
import com.kissanbandhu.weather.history.entity.WeatherHistory;
import com.kissanbandhu.weather.history.repository.WeatherHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes the SAME event payload type Current Weather publishes
 * (PersistWeatherEvent) - the two domains share the message contract without
 * sharing a database table, which is the whole point of the event-driven fix.
 */
@Component
public class WeatherHistoryPersistListener {

    private static final Logger log = LoggerFactory.getLogger(WeatherHistoryPersistListener.class);

    private final WeatherHistoryRepository repository;

    public WeatherHistoryPersistListener(WeatherHistoryRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = "history.persist.queue", containerFactory = "retryingListenerFactory")
    @Transactional
    public void onCurrentWeatherPersisted(PersistWeatherEvent event) {
        CurrentWeatherResponseDto dto = event.weather();

        // Idempotency: the same geohash/observedAt pair can legitimately arrive
        // twice (e.g. a retried message after a broker redelivery). The unique
        // constraint on (geohash, observed_at) is the real guarantee; this
        // pre-check just avoids a noisy constraint-violation log on the happy path.
        if (repository.existsByGeohashAndObservedAt(dto.geohash(), dto.observedAt())) {
            log.debug("History row already recorded for geohash={} observedAt={}, skipping", dto.geohash(), dto.observedAt());
            return;
        }

        repository.save(WeatherHistory.builder()
            .geohash(dto.geohash())
            .latitude(dto.latitude())
            .longitude(dto.longitude())
            .temperatureCelsius(dto.temperatureCelsius())
            .humidityPercent(dto.humidityPercent())
            .pressureHpa(dto.pressureHpa())
            .weatherMain(dto.weatherMain())
            .observedAt(dto.observedAt())
            .build());
    }
}
