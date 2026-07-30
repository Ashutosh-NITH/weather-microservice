package com.kissanbandhu.weather.history.dto;

import java.io.Serializable;
import java.time.Instant;

public record HistoryMonthSummaryDto(
    String geohash,
    int year,
    int month,
    Double avgTemperatureCelsius,
    Double minTemperatureCelsius,
    Double maxTemperatureCelsius,
    Double avgHumidityPercent,
    int sampleCount,
    Instant computedAt,
    String source // "REDIS" | "DB"
) implements Serializable {}
