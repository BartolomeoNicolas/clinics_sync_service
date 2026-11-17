package com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository;

import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.SyncErrorEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code sync_error}.
 *
 * <p>La bitácora se consulta por fecha y por tipo: es lo que pide el endpoint de errores
 * (SYNC-M1-07) y lo que hace útil tenerla en la base en vez de solo en el log.
 */
public interface SyncErrorRepository extends JpaRepository<SyncErrorEntity, Long> {

    /** Los errores más recientes primero, para paginar desde arriba. */
    List<SyncErrorEntity> findTop100ByOrderByOccurredAtDesc();
}
