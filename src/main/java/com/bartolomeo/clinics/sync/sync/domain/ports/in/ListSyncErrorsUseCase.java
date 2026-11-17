package com.bartolomeo.clinics.sync.sync.domain.ports.in;

import com.bartolomeo.clinics.sync.sync.domain.model.SyncError;
import java.util.List;

/**
 * Los errores de la última sincronización, consultables sin tocar la base.
 */
public interface ListSyncErrorsUseCase {

    /**
     * Los errores más recientes, del más nuevo al más viejo.
     *
     * @return los errores registrados
     */
    List<SyncError> recentErrors();
}
