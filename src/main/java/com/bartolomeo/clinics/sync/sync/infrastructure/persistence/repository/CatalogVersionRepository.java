package com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository;

import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code catalog_version}.
 *
 * <p>La restricción única sobre {@code version} es la red de seguridad final de la
 * idempotencia: aunque dos procesos intentaran registrar la misma versión a la vez, la base
 * solo deja pasar uno.
 */
public interface CatalogVersionRepository extends JpaRepository<CatalogVersionEntity, Long> {

    /** Si esta versión ya quedó aplicada antes. */
    Optional<CatalogVersionEntity> findByVersion(long version);

    boolean existsByVersion(long version);
}
