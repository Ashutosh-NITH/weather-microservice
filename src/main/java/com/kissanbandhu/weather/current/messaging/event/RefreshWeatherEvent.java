package com.kissanbandhu.weather.current.messaging.event;

/**
 * Published to weather.current.refresh - either by the API path (cache miss)
 * or by RefreshScheduler (stale active cell). Consumed by RefreshWeatherWorker.
 */
public record RefreshWeatherEvent(String geohash, double latitude, double longitude) {}
