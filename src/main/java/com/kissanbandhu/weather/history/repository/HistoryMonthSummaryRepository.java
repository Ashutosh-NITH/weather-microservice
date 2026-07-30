package com.kissanbandhu.weather.history.repository;

import com.kissanbandhu.weather.history.entity.HistoryMonthSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HistoryMonthSummaryRepository extends JpaRepository<HistoryMonthSummary, Long> {
    Optional<HistoryMonthSummary> findByGeohashAndYearAndMonth(String geohash, Integer year, Integer month);
}
