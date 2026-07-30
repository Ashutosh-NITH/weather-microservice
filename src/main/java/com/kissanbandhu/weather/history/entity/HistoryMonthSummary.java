package com.kissanbandhu.weather.history.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "history_month_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryMonthSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String geohash;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month;

    @Column(name = "avg_temperature_celsius")
    private Double avgTemperatureCelsius;

    @Column(name = "min_temperature_celsius")
    private Double minTemperatureCelsius;

    @Column(name = "max_temperature_celsius")
    private Double maxTemperatureCelsius;

    @Column(name = "avg_humidity_percent")
    private Double avgHumidityPercent;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        computedAt = Instant.now();
    }
}
