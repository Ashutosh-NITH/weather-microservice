package com.kissanbandhu.weather.history.repository;

public record MonthlyAggregate(
    Double avgTemperatureCelsius,
    Double minTemperatureCelsius,
    Double maxTemperatureCelsius,
    Double avgHumidityPercent,
    long sampleCount
) {}
