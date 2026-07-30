-- V3: History domain.
--
-- IMPORTANT: OpenWeather's FREE tier does not include a historical-weather
-- endpoint (that's a paid One Call 3.0 feature). Rather than requiring a
-- paid subscription, this service builds its own historical record by
-- listening to weather.current.persisted events emitted whenever the
-- Current Weather domain saves a fresh reading - so weather_history grows
-- organically from real traffic instead of backfilling from OpenWeather.
-- This also fixes the design-review finding that History Summary Worker
-- should consume events rather than read current_weather's table directly.

CREATE TABLE weather_history (
    id                  BIGSERIAL PRIMARY KEY,
    geohash             VARCHAR(12) NOT NULL,
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    temperature_celsius DOUBLE PRECISION,
    humidity_percent    INTEGER,
    pressure_hpa        INTEGER,
    weather_main        VARCHAR(64),
    observed_at         TIMESTAMPTZ NOT NULL,
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_weather_history_geohash_observed_at UNIQUE (geohash, observed_at)
);

CREATE INDEX idx_weather_history_geohash_observed_at ON weather_history (geohash, observed_at);

CREATE TABLE history_month_summary (
    id                  BIGSERIAL PRIMARY KEY,
    geohash             VARCHAR(12) NOT NULL,
    year                INTEGER NOT NULL,
    month               INTEGER NOT NULL,
    avg_temperature_celsius DOUBLE PRECISION,
    min_temperature_celsius DOUBLE PRECISION,
    max_temperature_celsius DOUBLE PRECISION,
    avg_humidity_percent    DOUBLE PRECISION,
    sample_count        INTEGER NOT NULL,
    computed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_history_month_summary UNIQUE (geohash, year, month)
);

CREATE INDEX idx_history_month_summary_geohash ON history_month_summary (geohash, year, month);

-- Retention: weather_history grows indefinitely from live event traffic.
-- Prune rows older than N months (e.g. 13, to always keep a trailing year
-- for month-over-month comparisons) via a scheduled DELETE rather than a
-- TimescaleDB hypertable policy - same plain-Postgres choice made for
-- current_weather/forecast_weather, for Neon compatibility.
