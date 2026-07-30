package com.kissanbandhu.weather.current.messaging.event;

import com.kissanbandhu.weather.current.dto.CurrentWeatherResponseDto;

/**
 * Published to weather.current.persist by RefreshWeatherWorker AFTER it has
 * already synchronously updated Redis. Consumed by DbPersistWorker, which
 * upserts current_weather. Kept async on purpose: the API caller (and Redis)
 * don't need to wait on a DB write.
 */
public record PersistWeatherEvent(CurrentWeatherResponseDto weather) {}
