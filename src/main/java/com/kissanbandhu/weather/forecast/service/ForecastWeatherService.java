package com.kissanbandhu.weather.forecast.service;

import com.kissanbandhu.weather.forecast.dto.ForecastResponseDto;

public interface ForecastWeatherService {

    /**
     * @param clientKey identifies the caller for per-client rate limiting
     *                  (authenticated username/subject from the JWT).
     */
    ForecastResponseDto getForecast(double latitude, double longitude, String clientKey);
}
