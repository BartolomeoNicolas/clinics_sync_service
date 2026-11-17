package com.bartolomeo.clinics.sync.sync.application.usecases;

import com.bartolomeo.clinics.sync.catalog.domain.model.SyncState;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogChangesPort;
import com.bartolomeo.clinics.sync.sync.domain.model.SyncStatus;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.GetSyncStatusUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.CatalogVersionPort;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncDiagnosticsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * El estado de la sincronización.
 *
 * <p><strong>Las versiones se leen de donde están.</strong> La local, del historial de
 * versiones aplicadas; la remota y la ventana, del estado que publica la cátedra. No hay
 * ningún número guardado en un lado que pueda quedar viejo: lo que se consulta son las
 * fuentes.
 *
 * <p><strong>La cátedra puede no estar.</strong> Si el estado remoto no se puede leer, el
 * servicio todavía puede decir cuál es su versión local y si hay una sincronización en curso:
 * el estado que se devuelve es útil igual, y el que falta se dice que falta.
 */
@Component
@RequiredArgsConstructor
public class GetSyncStatusUseCaseImpl implements GetSyncStatusUseCase {

    private final CatalogVersionPort versionPort;
    private final CatalogChangesPort changesPort;
    private final SyncDiagnosticsPort diagnostics;

    @Override
    public SyncStatus status() {

        long local = versionPort.currentVersion();
        boolean running = diagnostics.isRunning();

        long remoto = 0L;
        long masVieja = 0L;
        try {
            SyncState state = changesPort.syncState();
            remoto = state.currentVersion();
            masVieja = state.oldestAvailableVersion();
        } catch (RuntimeException sinCatedra) {
            // La cátedra no está. El estado local se sigue pudiendo consultar.
            remoto = local;
            masVieja = local;
        }

        var ultima = diagnostics.lastSync().orElse(null);

        return new SyncStatus(
                local,
                remoto,
                masVieja,
                running,
                ultima == null ? null : ultima.finishedAt(),
                ultima == null ? null : ultima.outcome(),
                ultima == null ? local : ultima.version());
    }
}
