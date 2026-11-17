package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.util.List;

/**
 * IDs afectados por una versión incremental (anexo §14.5).
 *
 * <p>El JSON de {@code catedra:sync:changes:{version}} trae <strong>solo los IDs</strong>, no
 * las entidades: <em>"los arrays contienen IDs afectados, no las entidades completas"</em>.
 * Para aplicar la versión hay que leer el estado vigente de cada ID desde el Hash que
 * corresponda —{@link CatalogChangesPort#professionalCategories(java.util.Collection)} y
 * compañía—. Este registro no las trae, solo dice a quién hay que ir a buscar.
 *
 * <p>Un ID listado acá puede corresponder a una entidad que está deshabilitada o que ya no
 * existe: la baja es una actualización como cualquier otra, y se aplica leyendo el Hash.
 *
 * @param professionalCategoryIds IDs de {@code catedra:sync:professional-categories}
 * @param professionalIds         IDs de {@code catedra:sync:professionals}
 * @param weeklyScheduleIds       IDs de {@code catedra:sync:weekly-schedules}
 */
public record CatalogChanges(
        long version,
        List<Long> professionalCategoryIds,
        List<Long> professionalIds,
        List<Long> weeklyScheduleIds) {

    /** Normaliza los tres arrays a vacío en lugar de {@code null}. */
    public CatalogChanges {
        professionalCategoryIds = professionalCategoryIds == null
                ? List.of() : List.copyOf(professionalCategoryIds);
        professionalIds = professionalIds == null ? List.of() : List.copyOf(professionalIds);
        weeklyScheduleIds = weeklyScheduleIds == null ? List.of() : List.copyOf(weeklyScheduleIds);
    }

    /** Si esta versión no toca nada: se avanza la versión local sin leer entidades. */
    public boolean isEmpty() {
        return professionalCategoryIds.isEmpty()
                && professionalIds.isEmpty()
                && weeklyScheduleIds.isEmpty();
    }
}
