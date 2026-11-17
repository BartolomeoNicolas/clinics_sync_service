package com.bartolomeo.clinics.sync.sync.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * El evento {@code CatalogUpdated} de la cátedra.
 *
 * <p><strong>Los campos desconocidos se ignoran.</strong> El contrato dice que dentro de una
 * versión pueden agregarse campos opcionales y que los consumidores deben ignorarlos. Un campo
 * que no se conoce no es un error: si lo fuera, cada campo nuevo que agregue la cátedra
 * rompería a todos los que ya estaban suscritos.
 *
 * @param eventId       identificador del evento; la clave de idempotencia
 * @param eventType     tipo del evento, en el vocabulario de la cátedra
 * @param newVersion    versión del catálogo que hay disponible
 * @param occurredAt    cuándo ocurrió, según la cátedra
 * @param source        origen de la versión, por ejemplo {@code MANUAL} o {@code SCHEDULED}
 * @param schemaVersion versión del contrato con la que se escribió el evento
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogUpdatedEvent(
        String eventId,
        String eventType,
        Long newVersion,
        Instant occurredAt,
        String source,
        Integer schemaVersion) {
}
