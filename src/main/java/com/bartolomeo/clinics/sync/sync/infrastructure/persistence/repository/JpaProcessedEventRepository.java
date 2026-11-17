package com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository;

import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code processed_event}.
 *
 * <p>La clave primaria sobre {@code event_id} es la red de seguridad final de la idempotencia:
 * aunque dos procesos recibieran el mismo evento a la vez, la base solo deja pasar uno.
 */
public interface JpaProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {
}
