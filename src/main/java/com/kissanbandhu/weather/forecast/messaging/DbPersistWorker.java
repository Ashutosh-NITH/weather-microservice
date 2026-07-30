package com.kissanbandhu.weather.forecast.messaging;

import com.kissanbandhu.weather.forecast.dto.ForecastResponseDto;
import com.kissanbandhu.weather.forecast.entity.ForecastWeather;
import com.kissanbandhu.weather.forecast.messaging.event.PersistForecastEvent;
import com.kissanbandhu.weather.forecast.repository.ForecastWeatherRepository;
import com.kissanbandhu.weather.forecast.service.ForecastMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("forecastDbPersistWorker")
public class DbPersistWorker {

    private static final Logger log = LoggerFactory.getLogger(DbPersistWorker.class);

    private final ForecastWeatherRepository repository;
    private final ForecastMapper mapper;

    public DbPersistWorker(ForecastWeatherRepository repository, ForecastMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @RabbitListener(queues = "forecast.persist.queue", containerFactory = "retryingListenerFactory")
    @Transactional
    public void onPersistRequested(PersistForecastEvent event) {
        ForecastResponseDto dto = event.forecast();
        log.debug("Persisting forecast for geohash={}", dto.geohash());

        ForecastWeather entity = repository.findByGeohash(dto.geohash())
            .orElseGet(ForecastWeather::new);

        entity.setGeohash(dto.geohash());
        entity.setLatitude(dto.latitude());
        entity.setLongitude(dto.longitude());
        entity.setForecastJson(mapper.periodsToJson(dto.periods()));

        repository.save(entity);
    }
}
