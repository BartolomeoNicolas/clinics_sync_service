package com.bartolomeo.clinics.sync.sync.domain.model;

import java.time.Instant;

/**
 * El estado de la sincronización, como lo ve quien lo consulta.
 *
 * <p><strong>Las tres versiones van juntas.</strong> La local es lo que este servicio aplicó;
 * la remota es la que hay disponible en la cátedra; y la más vieja disponible es hasta dónde
 * se puede ir por incremental. Con las tres se entiende de una mirada si la copia está al día
 * y si todavía se puede seguir por incremental.
 *
 * <p><strong>El último resultado y si hay una sincronización en curso.</strong> Lo primero
 * dice cómo terminó la última vez; lo segundo dice si ahora mismo hay alguien trabajando. Son
 * preguntas distintas y las dos se contestan sin tocar la base de la cátedra.
 *
 * @param localVersion          versión del catálogo aplicada en la copia local
 * @param remoteVersion         versión del catálogo que hay disponible
 * @param oldestAvailableVersion versión más vieja de la que hay cambios
 * @param running               si hay una sincronización en curso
 * @param lastSyncAt            cuándo terminó la última sincronización
 * @param lastSyncOutcome       cómo terminó la última sincronización
 * @param lastSyncVersion       versión a la que llegó la última sincronización
 */
public record SyncStatus(
        long localVersion,
        long remoteVersion,
        long oldestAvailableVersion,
        boolean running,
        Instant lastSyncAt,
        String lastSyncOutcome,
        long lastSyncVersion) {
}
