package com.kissanbandhu.weather.forecast.messaging.event;

import com.kissanbandhu.weather.forecast.dto.ForecastResponseDto;

public record PersistForecastEvent(ForecastResponseDto forecast) {}
