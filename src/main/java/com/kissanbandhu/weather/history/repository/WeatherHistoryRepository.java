package com.kissanbandhu.weather.history.repository;

import com.kissanbandhu.weather.history.entity.WeatherHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WeatherHistoryRepository extends JpaRepository<WeatherHistory, Long> {

    boolean existsByGeohashAndObservedAt(String geohash, Instant observedAt);

    @Query("select distinct w.geohash from WeatherHistory w where w.observedAt >= :from and w.observedAt < :to")
    List<String> findDistinctGeohashesInRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        select new com.kissanbandhu.weather.history.repository.MonthlyAggregate(
            avg(w.temperatureCelsius), min(w.temperatureCelsius), max(w.temperatureCelsius),
            avg(cast(w.humidityPercent as double)), count(w)
        )
        from WeatherHistory w
        where w.geohash = :geohash and w.observedAt >= :from and w.observedAt < :to
        """)
    MonthlyAggregate aggregateForMonth(@Param("geohash") String geohash,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);
}
