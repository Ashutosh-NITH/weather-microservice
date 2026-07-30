package com.kissanbandhu.weather.current.repository;

import com.kissanbandhu.weather.current.entity.CurrentWeather;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CurrentWeatherRepository extends JpaRepository<CurrentWeather, Long> {

    Optional<CurrentWeather> findByGeohash(String geohash);

    @Modifying
    @Query("delete from CurrentWeather c where c.geohash = :geohash")
    void deleteByGeohash(@Param("geohash") String geohash);

    // Used by RefreshScheduler: geohash cells whose row is older than the
    // staleness threshold need an active refresh, even if no one requests them.
    @Query("select c.geohash from CurrentWeather c where c.updatedAt < :staleBefore")
    List<String> findStaleGeohashes(@Param("staleBefore") Instant staleBefore);
}
