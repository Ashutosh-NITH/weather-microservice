-- V1: shared tracked_location table + current_weather domain table
-- tracked_location is written to by whichever domain (current/forecast/history)
-- first sees a geohash cell requested by a farmer, and read by all three
-- schedulers to know which cells are "active" and need periodic refresh.

CREATE TABLE tracked_location (
    id              BIGSERIAL PRIMARY KEY,
    geohash         VARCHAR(12)     NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    last_requested_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_tracked_location_geohash UNIQUE (geohash)
);

CREATE INDEX idx_tracked_location_last_requested_at ON tracked_location (last_requested_at);

CREATE TABLE current_weather (
    id                  BIGSERIAL PRIMARY KEY,
    geohash             VARCHAR(12) NOT NULL,
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    temperature_celsius DOUBLE PRECISION,
    feels_like_celsius  DOUBLE PRECISION,
    humidity_percent    INTEGER,
    pressure_hpa        INTEGER,
    wind_speed_mps       DOUBLE PRECISION,
    wind_direction_deg  INTEGER,
    cloudiness_percent  INTEGER,
    weather_main        VARCHAR(64),
    weather_description VARCHAR(128),
    weather_icon        VARCHAR(16),
    observed_at         TIMESTAMPTZ NOT NULL,
    fetched_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_current_weather_geohash UNIQUE (geohash)
);

CREATE INDEX idx_current_weather_updated_at ON current_weather (updated_at);
