package com.kissanbandhu.weather.forecast.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kissanbandhu.weather.common.client.dto.OpenWeatherForecastResponse;
import com.kissanbandhu.weather.forecast.dto.ForecastPeriodDto;
import com.kissanbandhu.weather.forecast.dto.ForecastResponseDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ForecastMapper {

    private final ObjectMapper objectMapper;

    public ForecastMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ForecastResponseDto toDto(OpenWeatherForecastResponse response, String geohash,
                                      double latitude, double longitude, String source) {
        List<ForecastPeriodDto> periods = response.list().stream()
            .map(this::toPeriodDto)
            .toList();
        return new ForecastResponseDto(geohash, latitude, longitude, periods, Instant.now(), source);
    }

    private ForecastPeriodDto toPeriodDto(OpenWeatherForecastResponse.Period period) {
        var weather = period.weather() != null && !period.weather().isEmpty()
            ? period.weather().get(0) : null;
        var main = period.main();
        var wind = period.wind();
        var clouds = period.clouds();

        return new ForecastPeriodDto(
            Instant.ofEpochSecond(period.dt()),
            main != null ? main.temp() : null,
            main != null ? main.feelsLike() : null,
            main != null ? main.humidity() : null,
            main != null ? main.pressure() : null,
            wind != null ? wind.speed() : null,
            wind != null ? wind.direction() : null,
            clouds != null ? clouds.all() : null,
            weather != null ? weather.main() : null,
            weather != null ? weather.description() : null,
            weather != null ? weather.icon() : null,
            period.pop()
        );
    }

    /** Serializes the list of periods for storage in forecast_weather.forecast_json. */
    public String periodsToJson(List<ForecastPeriodDto> periods) {
        try {
            return objectMapper.writeValueAsString(periods);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize forecast periods", e);
        }
    }

    public List<ForecastPeriodDto> periodsFromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ForecastPeriodDto>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize forecast periods", e);
        }
    }
}
