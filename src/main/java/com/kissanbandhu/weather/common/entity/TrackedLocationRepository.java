package com.kissanbandhu.weather.common.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackedLocationRepository extends JpaRepository<TrackedLocation, Long> {
    Optional<TrackedLocation> findByGeohash(String geohash);
}
