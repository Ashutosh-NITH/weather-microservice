package com.kissanbandhu.weather.forecast.messaging.event;

public record RefreshForecastEvent(String geohash, double latitude, double longitude) {}
