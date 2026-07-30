package com.kissanbandhu.weather.current.messaging;

import com.kissanbandhu.weather.current.dto.CurrentWeatherResponseDto;
import com.kissanbandhu.weather.current.entity.CurrentWeather;
import com.kissanbandhu.weather.current.messaging.event.CurrentWeatherPersistedApplicationEvent;
import com.kissanbandhu.weather.current.messaging.event.PersistWeatherEvent;
import com.kissanbandhu.weather.current.repository.CurrentWeatherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("currentDbPersistWorker")
public class DbPersistWorker {

    private static final Logger log = LoggerFactory.getLogger(DbPersistWorker.class);

    private final CurrentWeatherRepository repository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DbPersistWorker(CurrentWeatherRepository repository, ApplicationEventPublisher applicationEventPublisher) {
        this.repository = repository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @RabbitListener(queues = "current.persist.queue", containerFactory = "retryingListenerFactory")
    @Transactional
    public void onPersistRequested(PersistWeatherEvent event) {
        CurrentWeatherResponseDto dto = event.weather();
        log.debug("Persisting current weather for geohash={}", dto.geohash());

        CurrentWeather entity = repository.findByGeohash(dto.geohash())
            .orElseGet(CurrentWeather::new);

        entity.setGeohash(dto.geohash());
        entity.setLatitude(dto.latitude());
        entity.setLongitude(dto.longitude());
        entity.setTemperatureCelsius(dto.temperatureCelsius());
        entity.setFeelsLikeCelsius(dto.feelsLikeCelsius());
        entity.setHumidityPercent(dto.humidityPercent());
        entity.setPressureHpa(dto.pressureHpa());
        entity.setWindSpeedMps(dto.windSpeedMps());
        entity.setWindDirectionDeg(dto.windDirectionDeg());
        entity.setCloudinessPercent(dto.cloudinessPercent());
        entity.setWeatherMain(dto.weatherMain());
        entity.setWeatherDescription(dto.weatherDescription());
        entity.setWeatherIcon(dto.weatherIcon());
        entity.setObservedAt(dto.observedAt());

        repository.save(entity);

        // In-process only, for now - CurrentWeatherPersistedRelay turns this into
        // a Rabbit message once (and only if) this transaction commits.
        applicationEventPublisher.publishEvent(new CurrentWeatherPersistedApplicationEvent(event));
    }
}
