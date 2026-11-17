package com.bartolomeo.clinics.sync.sync.infrastructure.web.dto;

import java.time.Instant;

/**
 * Un error de la sincronización, consultable por el endpoint de errores.
 *
 * <p><strong>El detalle no sale.</strong> Es la traza completa, con mensajes de excepciones
 * del framework: sirve para el que investiga en los logs, no para el que consulta el
 * estado. Por eso el endpoint expone el mensaje y no el detalle.
 *
 * @param occurredAt       cuándo ocurrió
 * @param kind             de qué falló
 * @param attemptedVersion versión con la que se estaba trabajando
 * @param message          qué pasó
 */
public record SyncErrorResponse(
        Instant occurredAt,
        String kind,
        Long attemptedVersion,
        String message) {
}
