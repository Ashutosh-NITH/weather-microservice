package com.kissanbandhu.weather.forecast.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ForecastResponseDto(
    String geohash,
    double latitude,
    double longitude,
    List<ForecastPeriodDto> periods,
    Instant fetchedAt,
    String source // "REDIS" | "DB" | "OPENWEATHER"
) implements Serializable {}
