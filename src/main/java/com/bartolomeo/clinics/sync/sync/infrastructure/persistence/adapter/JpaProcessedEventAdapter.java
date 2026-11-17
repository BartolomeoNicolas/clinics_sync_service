package com.bartolomeo.clinics.sync.sync.infrastructure.persistence.adapter;

import com.bartolomeo.clinics.sync.sync.domain.ports.out.ProcessedEventRepository;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.ProcessedEventEntity;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.JpaProcessedEventRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementa el port de salida de eventos procesados sobre Spring Data.
 */
@Component
@RequiredArgsConstructor
public class JpaProcessedEventAdapter implements ProcessedEventRepository {

    private final JpaProcessedEventRepository repository;
    private final Clock clock;

    @Override
    public boolean existsByEventId(String eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void save(String eventId, int schemaVersion, Long appliedVersion) {
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.setEventId(eventId);
        entity.setSchemaVersion(schemaVersion);
        entity.setAppliedVersion(appliedVersion);
        entity.setReceivedAt(Instant.now(clock));
        repository.save(entity);
    }
}
