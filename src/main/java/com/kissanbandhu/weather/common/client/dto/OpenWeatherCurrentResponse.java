package com.kissanbandhu.weather.common.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherCurrentResponse(
    List<Weather> weather,
    Main main,
    Wind wind,
    Clouds clouds,
    long dt // unix timestamp (UTC) of data computation, becomes observed_at
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Weather(String main, String description, String icon) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
        double temp,
        @JsonProperty("feels_like") double feelsLike,
        int humidity,
        int pressure
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(double speed, @JsonProperty("deg") int direction) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clouds(int all) {}
}
