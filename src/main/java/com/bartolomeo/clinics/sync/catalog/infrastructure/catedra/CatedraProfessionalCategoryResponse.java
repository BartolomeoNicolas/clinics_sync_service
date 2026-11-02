package com.bartolomeo.clinics.sync.catalog.infrastructure.catedra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Categoría profesional dentro del snapshot y dentro de los Hashes de Redis (anexo §7 y §14.4).
 *
 * <p>Una misma forma para las dos fuentes: el snapshot y el Hash traen el mismo objeto, así que
 * un solo DTO cubre ambas y no hay dos maneras de leer la misma entidad.
 *
 * <p>{@code enabled} se conserva aunque sea {@code false}: es una baja lógica y filtrarla acá
 * haría desaparecer entidades que la cátedra sigue publicando.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CatedraProfessionalCategoryResponse(
        long id,
        String name,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
