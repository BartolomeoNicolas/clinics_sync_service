package com.bartolomeo.clinics.sync.sync.domain.ports.in;

import com.bartolomeo.clinics.sync.sync.domain.model.SyncStatus;

/**
 * El estado de la sincronización.
 */
public interface GetSyncStatusUseCase {

    /**
     * El estado actual: versiones, si hay una sincronización en curso y cómo terminó la
     * última.
     *
     * @return el estado
     */
    SyncStatus status();
}
