package com.bartolomeo.clinics.sync.sync.domain.model;

import java.time.Instant;

/**
 * Un intento de sincronización que terminó mal, tal como queda registrado en
 * {@code sync_error} (enunciado §11, punto 15: <em>"informar errores de integracion y
 * recuperarse sin corromper el estado local"</em>).
 *
 * <p><strong>Es un registro, no una excepción.</strong> La excepción se lanza para que el
 * llamador se entere; este registro es lo que queda después, para poder consultar qué pasó
 * sin tener que reproducirlo. Por eso los campos son los que sirven a quien lee: cuándo, de
 * qué tipo, contra qué versión, qué dijo y el detalle completo.
 *
 * <p>{@code attemptedVersion} puede ser {@code null}: si el fallo fue pedir el snapshot, todavía
 * no se sabía contra qué versión se trabajaba. Preferible dejarlo vacío antes que inventar un
 * número que despiste a quien investigue.
 *
 * @param occurredAt       cuándo falló, en UTC
 * @param kind             de qué falló, estable y en minúsculas ({@code snapshot},
 *                         {@code incremental}, {@code discontinuity})
 * @param attemptedVersion versión con la que se intentó, o {@code null} si no se llegó a saber
 * @param message          qué dijo el fallo, acotado al largo de la columna
 * @param detail           traza completa, para depurar sin volver a romperlo
 */
public record SyncError(
        Instant occurredAt,
        String kind,
        Long attemptedVersion,
        String message,
        String detail) {

    /**
     * Registra un fallo ocurrido ahora.
     *
     * <p>El reloj vive acá y no en el adaptador para que el registro sea un valor completo
     * desde que nace: quien lo recibe ya lo puede guardar tal cual, sin que la persistencia
     * tenga que rellenarle un campo.
     */
    public static SyncError now(String kind, Long attemptedVersion, String message, String detail) {
        return new SyncError(Instant.now(), kind, attemptedVersion, message, detail);
    }
}
