package com.kissanbandhu.weather.current.service;

import com.kissanbandhu.weather.current.dto.CurrentWeatherResponseDto;

public interface CurrentWeatherService {

    /**
     * @param clientKey identifies the caller for per-client rate limiting
     *                  (authenticated username/subject from the JWT).
     */
    CurrentWeatherResponseDto getCurrentWeather(double latitude, double longitude, String clientKey);
}
