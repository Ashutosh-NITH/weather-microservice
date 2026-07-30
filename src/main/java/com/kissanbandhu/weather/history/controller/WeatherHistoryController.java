package com.kissanbandhu.weather.history.controller;

import com.kissanbandhu.weather.history.dto.HistoryMonthSummaryDto;
import com.kissanbandhu.weather.history.service.WeatherHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather/history")
@Validated
@Tag(name = "Weather History", description = "Monthly weather summaries built from Current Weather's live event stream")
public class WeatherHistoryController {

    private final WeatherHistoryService weatherHistoryService;

    public WeatherHistoryController(WeatherHistoryService weatherHistoryService) {
        this.weatherHistoryService = weatherHistoryService;
    }

    @GetMapping("/monthly")
    @Operation(summary = "Get a monthly weather summary for a coordinate",
        description = "Rate limited to 1 request per 15 minutes per authenticated client. "
            + "Data is built entirely from Current Weather's persisted-event stream, not a paid OpenWeather history call.")
    public HistoryMonthSummaryDto getMonthlySummary(
        @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @RequestParam @NotNull @Min(2000) @Max(2100) Integer year,
        @RequestParam @NotNull @Min(1) @Max(12) Integer month) {

        return weatherHistoryService.getMonthSummary(latitude, longitude, year, month, resolveClientKey());
    }

    private String resolveClientKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated principal found on request");
        }
        return authentication.getName();
    }
}
