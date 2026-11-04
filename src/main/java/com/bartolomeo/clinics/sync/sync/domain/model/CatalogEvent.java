package com.bartolomeo.clinics.sync.sync.domain.model;

/**
 * La notificación de que hay una versión nueva del catálogo.
 *
 * <p><strong>Kafka avisa, no manda los datos.</strong> El evento dice qué versión hay
 * disponible, no qué cambió: los datos están en Redis, y lo que se sincroniza se decide
 * comparando versiones. Por eso el evento no trae el catálogo y por eso perder uno no
 * pierde información: el próximo aviso, o la comparación de versiones, recupera el atraso.
 *
 * @param eventId       identificador del evento; la clave de idempotencia funcional
 * @param newVersion    versión del catálogo que hay disponible
 * @param schemaVersion versión del contrato con la que se escribió el evento
 */
public record CatalogEvent(String eventId, long newVersion, int schemaVersion) {
}
