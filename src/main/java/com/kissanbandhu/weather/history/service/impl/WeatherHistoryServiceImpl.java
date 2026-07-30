package com.kissanbandhu.weather.history.service.impl;

import com.kissanbandhu.weather.common.config.PerClientRateLimiter;
import com.kissanbandhu.weather.common.exception.RateLimitExceededException;
import com.kissanbandhu.weather.common.exception.ResourceNotFoundException;
import com.kissanbandhu.weather.common.geohash.GeoHashService;
import com.kissanbandhu.weather.history.dto.HistoryMonthSummaryDto;
import com.kissanbandhu.weather.history.entity.HistoryMonthSummary;
import com.kissanbandhu.weather.history.repository.HistoryMonthSummaryRepository;
import com.kissanbandhu.weather.history.repository.MonthlyAggregate;
import com.kissanbandhu.weather.history.repository.WeatherHistoryRepository;
import com.kissanbandhu.weather.history.service.WeatherHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class WeatherHistoryServiceImpl implements WeatherHistoryService {

    private static final Logger log = LoggerFactory.getLogger(WeatherHistoryServiceImpl.class);
    private static final String CACHE_PREFIX = "history:summary:";
    private static final Duration CLOSED_MONTH_TTL = Duration.ofDays(30); // past months never change
    private static final Duration CURRENT_MONTH_TTL = Duration.ofHours(1); // still accumulating samples

    private final PerClientRateLimiter rateLimiter;
    private final GeoHashService geoHashService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final HistoryMonthSummaryRepository summaryRepository;
    private final WeatherHistoryRepository weatherHistoryRepository;

    public WeatherHistoryServiceImpl(PerClientRateLimiter rateLimiter,
                                      GeoHashService geoHashService,
                                      RedisTemplate<String, Object> redisTemplate,
                                      HistoryMonthSummaryRepository summaryRepository,
                                      WeatherHistoryRepository weatherHistoryRepository) {
        this.rateLimiter = rateLimiter;
        this.geoHashService = geoHashService;
        this.redisTemplate = redisTemplate;
        this.summaryRepository = summaryRepository;
        this.weatherHistoryRepository = weatherHistoryRepository;
    }

    @Override
    public HistoryMonthSummaryDto getMonthSummary(double latitude, double longitude, int year, int month, String clientKey) {
        if (!rateLimiter.tryAcquire(clientKey)) {
            throw new RateLimitExceededException("Rate limit exceeded: 1 request per 15 minutes per client");
        }

        String geohash = geoHashService.encode(latitude, longitude);
        String cacheKey = CACHE_PREFIX + geohash + ":" + year + "-" + month;

        HistoryMonthSummaryDto cached = (HistoryMonthSummaryDto) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Optional<HistoryMonthSummary> dbRow = summaryRepository.findByGeohashAndYearAndMonth(geohash, year, month);
        if (dbRow.isPresent()) {
            HistoryMonthSummaryDto dto = toDto(dbRow.get(), "DB");
            cache(cacheKey, dto, year, month);
            return dto;
        }

        // Scheduler hasn't produced a summary for this month yet (e.g. the
        // current, still-in-progress month) - compute it on demand instead of
        // making the caller wait for the 1st-of-next-month batch job.
        YearMonth yearMonth = YearMonth.of(year, month);
        var from = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        MonthlyAggregate aggregate = weatherHistoryRepository.aggregateForMonth(geohash, from, to);
        if (aggregate == null || aggregate.sampleCount() == 0) {
            throw new ResourceNotFoundException(
                "No history data available yet for this location in " + year + "-" + month);
        }

        HistoryMonthSummaryDto dto = new HistoryMonthSummaryDto(
            geohash, year, month,
            aggregate.avgTemperatureCelsius(), aggregate.minTemperatureCelsius(), aggregate.maxTemperatureCelsius(),
            aggregate.avgHumidityPercent(), (int) aggregate.sampleCount(), Instant.now(), "DB"
        );
        cache(cacheKey, dto, year, month);
        return dto;
    }

    private void cache(String cacheKey, HistoryMonthSummaryDto dto, int year, int month) {
        boolean isCurrentMonth = YearMonth.now(ZoneOffset.UTC).equals(YearMonth.of(year, month));
        redisTemplate.opsForValue().set(cacheKey, dto, isCurrentMonth ? CURRENT_MONTH_TTL : CLOSED_MONTH_TTL);
    }

    private HistoryMonthSummaryDto toDto(HistoryMonthSummary entity, String source) {
        return new HistoryMonthSummaryDto(
            entity.getGeohash(), entity.getYear(), entity.getMonth(),
            entity.getAvgTemperatureCelsius(), entity.getMinTemperatureCelsius(), entity.getMaxTemperatureCelsius(),
            entity.getAvgHumidityPercent(), entity.getSampleCount(), entity.getComputedAt(), source
        );
    }
}
