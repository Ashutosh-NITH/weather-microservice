-- V2: Forecast domain. Uses OpenWeather's free-tier 5-day/3-hour-step
-- endpoint (/data/2.5/forecast), so one row per geohash holds the whole
-- 5-day forecast as a JSON blob of periods rather than one row per period -
-- that keeps the write path a single upsert, same shape as current_weather.

CREATE TABLE forecast_weather (
    id              BIGSERIAL PRIMARY KEY,
    geohash         VARCHAR(12) NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    forecast_json   TEXT NOT NULL,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_forecast_weather_geohash UNIQUE (geohash)
);

CREATE INDEX idx_forecast_weather_updated_at ON forecast_weather (updated_at);

-- TimescaleDB was evaluated and rejected in favor of plain Postgres for Neon
-- compatibility (see design review history). If retention/pruning of old
-- forecast rows is ever needed, do it via a scheduled DELETE on updated_at
-- rather than a hypertable retention policy.
