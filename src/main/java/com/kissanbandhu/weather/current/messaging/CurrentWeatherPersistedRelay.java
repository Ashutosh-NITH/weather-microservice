package com.kissanbandhu.weather.current.messaging;

import com.kissanbandhu.weather.current.messaging.event.CurrentWeatherPersistedApplicationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishing a Rabbit message from inside an @Transactional method is unsafe:
 * the message can go out even if the surrounding transaction later rolls
 * back. Instead, DbPersistWorker publishes an in-process Spring
 * ApplicationEvent, and THIS listener - fired only in the AFTER_COMMIT phase
 * - is what actually puts the "weather.current.persisted" message on the
 * broker for the History domain to consume.
 */
@Component
public class CurrentWeatherPersistedRelay {

    private final WeatherEventPublisher eventPublisher;

    public CurrentWeatherPersistedRelay(WeatherEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPersistedAfterCommit(CurrentWeatherPersistedApplicationEvent event) {
        eventPublisher.publishPersisted(event.payload());
    }
}
