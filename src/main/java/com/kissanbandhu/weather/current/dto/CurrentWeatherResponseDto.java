package com.kissanbandhu.weather.current.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Implements Serializable purely so it round-trips cleanly through Redis via
 * GenericJackson2JsonRedisSerializer (JSON, not Java serialization - this
 * marker isn't strictly required by Jackson, but keeps the DTO
 * cache-storage-agnostic if the serializer ever changes).
 */
public record CurrentWeatherResponseDto(
    String geohash,
    double latitude,
    double longitude,
    Double temperatureCelsius,
    Double feelsLikeCelsius,
    Integer humidityPercent,
    Integer pressureHpa,
    Double windSpeedMps,
    Integer windDirectionDeg,
    Integer cloudinessPercent,
    String weatherMain,
    String weatherDescription,
    String weatherIcon,
    Instant observedAt,
    Instant fetchedAt,
    String source // "REDIS" | "DB" | "OPENWEATHER" - handy for debugging/observability
) implements Serializable {}
