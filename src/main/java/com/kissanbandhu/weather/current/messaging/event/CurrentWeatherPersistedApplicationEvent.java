package com.kissanbandhu.weather.current.messaging.event;

/**
 * Spring ApplicationEvent (in-process, NOT a Rabbit message) published by
 * DbPersistWorker inside its @Transactional method. See
 * CurrentWeatherPersistedRelay for why this indirection exists.
 */
public record CurrentWeatherPersistedApplicationEvent(PersistWeatherEvent payload) {}
