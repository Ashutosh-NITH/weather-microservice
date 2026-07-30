package com.kissanbandhu.weather.common.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherForecastResponse(
    List<Period> list
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Period(
        long dt,
        Main main,
        List<OpenWeatherCurrentResponse.Weather> weather,
        OpenWeatherCurrentResponse.Clouds clouds,
        Wind wind,
        Double pop // probability of precipitation, 0.0-1.0
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
        double temp,
        @JsonProperty("feels_like") double feelsLike,
        int humidity,
        int pressure
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(double speed, @JsonProperty("deg") int direction) {}
}
