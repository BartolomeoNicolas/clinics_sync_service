package com.bartolomeo.clinics.sync.sync.application.usecases;

import com.bartolomeo.clinics.sync.sync.domain.model.SyncError;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.ListSyncErrorsUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncErrorPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Los errores de la última sincronización, consultables sin tocar la base.
 *
 * <p><strong>El que consulta no abre la base.</strong> Los errores quedan registrados en la
 * tabla de control del servicio y se piden por el endpoint: el que mira el estado del
 * servicio no necesita credenciales de base ni saber el esquema.
 */
@Component
@RequiredArgsConstructor
public class ListSyncErrorsUseCaseImpl implements ListSyncErrorsUseCase {

    private final SyncErrorPort errorPort;

    @Override
    public List<SyncError> recentErrors() {
        return errorPort.recent();
    }
}
