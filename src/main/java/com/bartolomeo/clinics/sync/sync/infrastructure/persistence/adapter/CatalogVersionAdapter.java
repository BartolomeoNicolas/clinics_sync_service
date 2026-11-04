package com.bartolomeo.clinics.sync.sync.infrastructure.persistence.adapter;

import com.bartolomeo.clinics.sync.sync.domain.ports.out.CatalogVersionPort;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.CatalogVersionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link CatalogVersionPort} sobre PostgreSQL.
 *
 * <p><strong>La verificación y la escritura van en la misma transacción que la réplica.</strong>
 * Este adaptador no abre una por su lado: se une a la que ya está abierta, que es la del caso de
 * uso y la que también reemplaza las tres colecciones. Así, o quedan aplicadas las tres cosas o
 * no queda ninguna.
 *
 * <p><strong>Registrar dos veces no cambia nada.</strong> Si la versión ya existe no se inserta
 * ni se le mueve {@code applied_at}: una operación repetida tiene que producir exactamente el
 * mismo estado que la primera (enunciado §8). La restricción única sobre {@code version} es la
 * red de seguridad por si dos procesos llegaran al mismo tiempo.
 */
@Component
@RequiredArgsConstructor
public class CatalogVersionAdapter implements CatalogVersionPort {

    private static final String FUENTE_SNAPSHOT = "SNAPSHOT";
    private static final String FUENTE_INCREMENTAL = "INCREMENTAL";

    private final CatalogVersionRepository repository;

    @Override
    public long currentVersion() {
        return repository.findTopByOrderByVersionDesc()
                .map(CatalogVersionEntity::getVersion)
                .orElse(0L);
    }

    @Override
    @Transactional
    public void recordSnapshotApplied(long version, Instant snapshotGeneratedAt) {

        if (repository.existsByVersion(version)) {
            return;
        }

        CatalogVersionEntity entity = new CatalogVersionEntity();
        entity.setVersion(version);
        entity.setSource(FUENTE_SNAPSHOT);
        entity.setSnapshotGeneratedAt(snapshotGeneratedAt);
        entity.setAppliedAt(Instant.now());
        repository.save(entity);
    }

    @Override
    @Transactional
    public void recordIncrementalApplied(long version, Instant publishedAt) {

        if (repository.existsByVersion(version)) {
            return;
        }

        CatalogVersionEntity entity = new CatalogVersionEntity();
        entity.setVersion(version);
        entity.setSource(FUENTE_INCREMENTAL);
        entity.setSnapshotGeneratedAt(publishedAt);
        entity.setAppliedAt(Instant.now());
        repository.save(entity);
    }
}
