package com.kissanbandhu.weather.forecast.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per geohash cell, holding the entire 5-day/3-hour-step forecast as
 * a JSON blob (see ForecastWeatherServiceImpl for (de)serialization). This
 * keeps the persist path a single upsert per refresh instead of replacing
 * ~40 individual period rows every cycle.
 */
@Entity
@Table(name = "forecast_weather")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastWeather {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String geohash;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "forecast_json", nullable = false, columnDefinition = "TEXT")
    private String forecastJson;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (fetchedAt == null) fetchedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
