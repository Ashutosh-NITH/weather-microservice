package com.kissanbandhu.weather.common.geohash;

import ch.hsr.geohash.GeoHash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Converts raw lat/lng into a fixed-precision geohash string.
 * Precision 7 -> ~153m x 153m cells. This is the single source of truth for
 * cache-key / DB-row granularity across current, forecast and history
 * domains: two farmers within the same cell share one OpenWeather call,
 * one Redis entry, and one DB row.
 */
@Service
public class GeoHashService {

    private final int precision;

    public GeoHashService(@Value("${weather.geohash.precision:7}") int precision) {
        this.precision = precision;
    }

    public String encode(double latitude, double longitude) {
        return GeoHash.withCharacterPrecision(latitude, longitude, precision).toBase32();
    }
}
