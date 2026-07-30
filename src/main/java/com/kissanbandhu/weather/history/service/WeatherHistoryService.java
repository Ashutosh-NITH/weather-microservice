package com.kissanbandhu.weather.history.service;

import com.kissanbandhu.weather.history.dto.HistoryMonthSummaryDto;

public interface WeatherHistoryService {

    HistoryMonthSummaryDto getMonthSummary(double latitude, double longitude, int year, int month, String clientKey);
}
