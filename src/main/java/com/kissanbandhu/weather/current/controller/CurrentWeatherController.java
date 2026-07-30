package com.kissanbandhu.weather.current.controller;

import com.kissanbandhu.weather.current.dto.CurrentWeatherResponseDto;
import com.kissanbandhu.weather.current.service.CurrentWeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/weather/current")
@Validated
@Tag(name = "Current Weather", description = "Real-time weather for a farmer's location, cached by geohash cell")
public class CurrentWeatherController {

    private final CurrentWeatherService currentWeatherService;

    public CurrentWeatherController(CurrentWeatherService currentWeatherService) {
        this.currentWeatherService = currentWeatherService;
    }

    @GetMapping
    @Operation(summary = "Get current weather for a coordinate",
        description = "Rate limited to 1 request per 15 minutes per authenticated client. "
            + "Resolves to a ~150m geohash cell so nearby farmers share the same cached reading.")
    public CurrentWeatherResponseDto getCurrentWeather(
        @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {

        return currentWeatherService.getCurrentWeather(latitude, longitude, resolveClientKey());
    }

    /**
     * kb-common's JwtAuthenticationFilter populates the SecurityContext with the
     * authenticated subject (farmer's user id / username) extracted from the JWT.
     * This is the "use Authentication/SecurityContextHolder in controllers" wiring
     * the auth service hands off to every downstream microservice.
     */
    private String resolveClientKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("No authenticated principal found on request");
        }
        return authentication.getName();
    }
}
