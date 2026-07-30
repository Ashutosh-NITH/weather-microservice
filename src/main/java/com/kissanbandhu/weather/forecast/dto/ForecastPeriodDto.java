package com.kissanbandhu.weather.forecast.dto;

import java.io.Serializable;
import java.time.Instant;

public record ForecastPeriodDto(
    Instant forecastFor,
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
    Double probabilityOfPrecipitation
) implements Serializable {}
