package com.kissanbandhu.weather.current.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "current_weather")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentWeather {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String geohash;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "feels_like_celsius")
    private Double feelsLikeCelsius;

    @Column(name = "humidity_percent")
    private Integer humidityPercent;

    @Column(name = "pressure_hpa")
    private Integer pressureHpa;

    @Column(name = "wind_speed_mps")
    private Double windSpeedMps;

    @Column(name = "wind_direction_deg")
    private Integer windDirectionDeg;

    @Column(name = "cloudiness_percent")
    private Integer cloudinessPercent;

    @Column(name = "weather_main")
    private String weatherMain;

    @Column(name = "weather_description")
    private String weatherDescription;

    @Column(name = "weather_icon")
    private String weatherIcon;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

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
