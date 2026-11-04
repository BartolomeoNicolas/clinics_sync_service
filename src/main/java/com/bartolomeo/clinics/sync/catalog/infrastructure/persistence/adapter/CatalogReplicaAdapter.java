package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.adapter;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogReplicaPort;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.mapper.CatalogReplicaMapper;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalCategoryRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.WeeklyScheduleRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
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

    /**
     * Aplica los cambios de una versión sobre la copia local.
     *
     * <p><strong>El upsert va en el orden de las claves ajenas.</strong> Una categoría que
     * aparece antes que el profesional que la referencia, y un profesional antes que el
     * horario que lo referencia: al revés, una FOREIGN KEY violada abortaría la transacción.
     *
     * <p><strong>No se borra nada.</strong> Un incremento solo trae lo que cambió, así que
     * borrar sería perder el catálogo entero. Las bajas lógicas llegan como entidades
     * deshabilitadas y se guardan así.
     */
    @Override
    @Transactional
    public void upsert(
            List<ProfessionalCategory> categories,
            List<Professional> professionals,
            List<WeeklySchedule> schedules) {

        if (!categories.isEmpty()) {
            this.categories.saveAll(categories.stream()
                    .map(CatalogReplicaMapper::toEntity)
                    .toList());
        }
        if (!professionals.isEmpty()) {
            this.professionals.saveAll(professionals.stream()
                    .map(CatalogReplicaMapper::toEntity)
                    .toList());
        }
        if (!schedules.isEmpty()) {
            this.schedules.saveAll(schedules.stream()
                    .map(CatalogReplicaMapper::toEntity)
                    .toList());
        }
        entityManager.flush();
    }
}
