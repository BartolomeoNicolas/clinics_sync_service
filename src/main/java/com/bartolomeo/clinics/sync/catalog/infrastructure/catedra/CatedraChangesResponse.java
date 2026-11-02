package com.bartolomeo.clinics.sync.catalog.infrastructure.catedra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Contenido de {@code catedra:sync:changes:{version}} (anexo §14.5).
 *
 * <p>Los arrays traen <strong>solo IDs</strong>, no entidades completas: para aplicar la
 * versión hay que ir a buscar cada una al Hash que corresponda. Este DTO no trae entidades
 * tampoco, porque la lectura por lote la decide el caso de uso con los IDs que salen de acá.
 *
 * <p>Se anida en {@link Changes} porque el JSON tiene dos niveles y un record planearía mal:
 * el campo se llama {@code changes} y dentro están las tres colecciones.
 *
 * <p>Tolerante a campos desconocidos, como todo lo que viene de la cátedra.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CatedraChangesResponse(long version, Changes changes) {

    /** IDs afectados por colección. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Changes(
            List<Long> professionalCategories,
            List<Long> professionals,
            List<Long> weeklySchedules) {
    }
}
