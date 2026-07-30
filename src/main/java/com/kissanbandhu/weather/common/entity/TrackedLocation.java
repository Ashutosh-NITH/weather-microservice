package com.kissanbandhu.weather.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A geohash cell that at least one farmer has requested weather for.
 * Schedulers (current/forecast/history) all read this table to know which
 * cells are "active" and worth refreshing on their respective intervals.
 */
@Entity
@Table(name = "tracked_location")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String geohash;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "last_requested_at", nullable = false)
    private Instant lastRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastRequestedAt == null) lastRequestedAt = now;
    }
}
