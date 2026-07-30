package com.kissanbandhu.weather.forecast.repository;

import com.kissanbandhu.weather.forecast.entity.ForecastWeather;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ForecastWeatherRepository extends JpaRepository<ForecastWeather, Long> {

    Optional<ForecastWeather> findByGeohash(String geohash);

    @Query("select f.geohash from ForecastWeather f where f.updatedAt < :staleBefore")
    List<String> findStaleGeohashes(@Param("staleBefore") Instant staleBefore);
}
