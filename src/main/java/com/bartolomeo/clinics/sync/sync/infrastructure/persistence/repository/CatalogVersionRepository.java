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

    /**
     * La versión más alta aplicada: la que es la versión local.
     *
     * <p>El historial es por versión a propósito —guardar solo la última perdería la
     * capacidad de detectar una discontinuidad—, así que la versión local se busca como la
     * más alta del historial y no como un singleton.
     *
     * @return la versión más alta registrada, o vacío si no hay ninguna
     */
    Optional<CatalogVersionEntity> findTopByOrderByVersionDesc();
}
