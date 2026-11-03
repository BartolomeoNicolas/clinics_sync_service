package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.adapter;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogReplicaPort;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.mapper.CatalogReplicaMapper;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalCategoryRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.WeeklyScheduleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link CatalogReplicaPort} sobre PostgreSQL.
 *
 * <p><strong>El orden no es un detalle: es la restricción de clave ajena.</strong> Al borrar
 * van primero las que apuntan y después las apuntadas —horarios, profesionales, categorías—
 * y al cargar al revés. Invertir cualquiera de los dos órdenes revienta contra
 * {@code fk_weekly_schedule_professional} o {@code fk_professional_category}, y como las tres
 * colecciones se cargan en la misma transacción, el fallo deja la copia anterior entera.
 *
 * <p><strong>{@code em.clear()} entre el borrado y la carga.</strong> Borrar en lote no vacía
 * el contexto de persistencia: si quedara administrada una entidad vieja con el mismo id, el
 * {@code merge} posterior la modificaría en vez de insertar y la escritura se perdería. Con el
 * contexto limpio cada fila del snapshot se inserta de verdad.
 *
 * <p>Todo corre dentro de la transacción que abre el caso de uso: acá no se confirma nada, solo
 * se acumulan escrituras revocables.
 */
@Component
@RequiredArgsConstructor
public class CatalogReplicaAdapter implements CatalogReplicaPort {

    private final WeeklyScheduleRepository schedules;
    private final ProfessionalRepository professionals;
    private final ProfessionalCategoryRepository categories;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void replaceAll(CatalogSnapshot snapshot) {

        // Borrado en orden inverso a las claves ajenas.
        schedules.deleteAllInBatch();
        professionals.deleteAllInBatch();
        categories.deleteAllInBatch();
        entityManager.clear();

        // Carga en orden directo: la categoría primero, porque el profesional la referencia.
        categories.saveAll(snapshot.professionalCategories().stream()
                .map(CatalogReplicaMapper::toEntity)
                .toList());
        professionals.saveAll(snapshot.professionals().stream()
                .map(CatalogReplicaMapper::toEntity)
                .toList());
        schedules.saveAll(snapshot.weeklySchedules().stream()
                .map(CatalogReplicaMapper::toEntity)
                .toList());

        // Fuerza los INSERT ahora y no al cerrar la transacción: si una fila viola una
        // restricción, que falle mientras todavía se puede revertir todo de una.
        entityManager.flush();
    }
}
