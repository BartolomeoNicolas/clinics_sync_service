package com.bartolomeo.clinics.sync.catalog.infrastructure.catedra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Profesional dentro del snapshot y dentro de los Hashes de Redis (anexo §7 y §14.4).
 *
 * <p>{@code categoryId} es un valor, no una referencia resuelta: la cátedra lo publica como
 * número y así se queda.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CatedraProfessionalResponse(
        long id,
        long categoryId,
        String firstName,
        String lastName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
