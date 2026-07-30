package com.kissanbandhu.weather.history.messaging.event;

public record RefreshMonthSummaryEvent(String geohash, int year, int month) {}
