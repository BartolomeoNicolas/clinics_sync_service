package com.bartolomeo.clinics.sync.sync.infrastructure.web.dto;

import java.time.Instant;

/**
 * El estado de la sincronización, como lo ve quien lo consulta.
 *
 * @param localVersion          versión del catálogo aplicada en la copia local
 * @param remoteVersion         versión del catálogo que hay disponible
 * @param oldestAvailableVersion versión más vieja de la que hay cambios
 * @param running               si hay una sincronización en curso
 * @param lastSyncAt            cuándo terminó la última sincronización
 * @param lastSyncOutcome       cómo terminó la última sincronización
 * @param lastSyncVersion       versión a la que llegó la última sincronización
 */
public record SyncStatusResponse(
        long localVersion,
        long remoteVersion,
        long oldestAvailableVersion,
        boolean running,
        Instant lastSyncAt,
        String lastSyncOutcome,
        long lastSyncVersion) {
}
