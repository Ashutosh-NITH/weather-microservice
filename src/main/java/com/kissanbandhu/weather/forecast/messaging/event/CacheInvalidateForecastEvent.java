package com.kissanbandhu.weather.forecast.messaging.event;

public record CacheInvalidateForecastEvent(String geohash) {}
