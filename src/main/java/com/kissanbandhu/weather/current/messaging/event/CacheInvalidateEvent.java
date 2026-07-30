package com.kissanbandhu.weather.current.messaging.event;

/**
 * Published to weather.current.cache.invalidate whenever the DB row for a
 * geohash changes outside of the normal Redis-then-DB write path (e.g. a
 * manual correction, or a future admin/back-office write). Consumed by
 * CacheInvalidateWorker, which evicts the stale Redis entry so the next read
 * falls through to a fresh DB/OpenWeather fetch instead of serving stale data.
 */
public record CacheInvalidateEvent(String geohash) {}
