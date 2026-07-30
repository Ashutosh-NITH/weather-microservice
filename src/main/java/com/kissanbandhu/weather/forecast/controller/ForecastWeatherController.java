package com.kissanbandhu.weather.forecast.controller;

import com.kissanbandhu.weather.forecast.dto.ForecastResponseDto;
import com.kissanbandhu.weather.forecast.service.ForecastWeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather/forecast")
@Validated
@Tag(name = "Forecast Weather", description = "5-day/3-hour-step forecast for a farmer's location, cached by geohash cell")
public class ForecastWeatherController {

    private final ForecastWeatherService forecastWeatherService;

    public ForecastWeatherController(ForecastWeatherService forecastWeatherService) {
        this.forecastWeatherService = forecastWeatherService;
    }

    @GetMapping
    @Operation(summary = "Get 5-day forecast for a coordinate",
        description = "Rate limited to 1 request per 15 minutes per authenticated client. "
            + "Same geohash cell + Redis/DB caching strategy as Current Weather, refreshed every 6 hours.")
    public ForecastResponseDto getForecast(
        @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {

        return forecastWeatherService.getForecast(latitude, longitude, resolveClientKey());
    }

    private String resolveClientKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated principal found on request");
        }
        return authentication.getName();
    }
}
