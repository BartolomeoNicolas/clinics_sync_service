package com.bartolomeo.clinics.sync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogChanges;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.SyncState;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogChangesPort;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogReplicaPort;
import com.bartolomeo.clinics.sync.sync.application.exception.SyncAlreadyRunningException;
import com.bartolomeo.clinics.sync.sync.application.usecases.SyncIncrementalUseCaseImpl;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncFullSnapshotUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncIncrementalUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.CatalogVersionPort;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncDiagnosticsPort;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncErrorPort;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncLockPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * La regla de versiones de la sincronización incremental.
 *
 * <p>Lo que se prueba acá es la decisión de fondo: qué se aplica, en qué orden y qué pasa
 * cuando la ventana ya no alcanza. El resto —que la copia quede escrita de verdad— lo prueba
 * el test de integración.
 */
@DisplayName("la sincronizacion incremental")
class SyncIncrementalUseCaseImplTest {

    private static final Instant PUBLICADO = Instant.parse("2026-09-10T13:44:59Z");

    private final CatalogChangesPort changesPort = mock(CatalogChangesPort.class);
    private final CatalogReplicaPort replicaPort = mock(CatalogReplicaPort.class);
    private final CatalogVersionPort versionPort = mock(CatalogVersionPort.class);
    private final SyncFullSnapshotUseCase snapshotUseCase = mock(SyncFullSnapshotUseCase.class);
    private final SyncErrorPort errorPort = mock(SyncErrorPort.class);
    private final SyncLockPort lock = mock(SyncLockPort.class);
    private final SyncDiagnosticsPort diagnostics = mock(SyncDiagnosticsPort.class);

    private final SyncIncrementalUseCase useCase = new SyncIncrementalUseCaseImpl(
            changesPort, replicaPort, versionPort, snapshotUseCase, errorPort, lock, diagnostics);

    @BeforeEach
    void preparar() {
        when(lock.tryAcquire(any())).thenReturn(true);
    }

    private static CatalogChanges cambios(long version, List<Long> profesionales) {
        return new CatalogChanges(version, List.of(), profesionales, List.of());
    }

    private void estadoRemoto(long current, long oldest) {
        when(changesPort.syncState())
                .thenReturn(new SyncState(current, oldest, PUBLICADO));
    }

    @Test
    @DisplayName("si ya hay una sincronizacion en curso no se empieza otra")
    void siYaHayUnaSincronizacionNoSeEmpiezaOtra() {
        when(versionPort.currentVersion()).thenReturn(3L);
        estadoRemoto(5, 3);
        when(lock.tryAcquire(any())).thenReturn(false);

        var error = catchThrowableOfType(
                SyncAlreadyRunningException.class,
                () -> useCase.applyIncremental());

        assertThat(error).isNotNull();
        verify(changesPort, never()).changes(anyLong());
        verify(replicaPort, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("el lock se suelta siempre, aunque la sincronizacion falle")
    void elLockSeSueltaSiempre() {
        when(versionPort.currentVersion()).thenReturn(3L);
        estadoRemoto(5, 3);
        when(changesPort.changes(4L)).thenThrow(new IllegalStateException("redis caido"));

        catchThrowableOfType(IllegalStateException.class, () -> useCase.applyIncremental());

        verify(lock).release();
    }

    @Test
    @DisplayName("aplica las versiones pendientes en orden")
    void aplicaLasVersionesPendientesEnOrden() {
        when(versionPort.currentVersion()).thenReturn(3L);
        estadoRemoto(5, 3);
        when(changesPort.changes(4L)).thenReturn(cambios(4, List.of(11L)));
        when(changesPort.changes(5L)).thenReturn(cambios(5, List.of(12L)));
        when(changesPort.professionals(anyCollection())).thenReturn(List.of());

        long version = useCase.applyIncremental();

        assertThat(version).isEqualTo(5L);

        InOrder orden = inOrder(changesPort, versionPort);
        orden.verify(changesPort).changes(4L);
        orden.verify(versionPort).recordIncrementalApplied(eq(4L), eq(PUBLICADO));
        orden.verify(changesPort).changes(5L);
        orden.verify(versionPort).recordIncrementalApplied(eq(5L), eq(PUBLICADO));
    }

    @Test
    @DisplayName("con discontinuidad pide un snapshot y no intenta leer los cambios")
    void conDiscontinuidadPideSnapshot() {
        // El caso del anexo 18.2: local 3, ventana desde la 4. Falta la versión que
        // conecta el estado local, así que no se intenta changes:4.
        when(versionPort.currentVersion()).thenReturn(3L);
        estadoRemoto(7, 4);
        when(snapshotUseCase.applyFullSnapshot()).thenReturn(7L);

        long version = useCase.applyIncremental();

        assertThat(version).isEqualTo(7L);
        verify(changesPort, never()).changes(anyLong());
        verify(snapshotUseCase).applyFullSnapshot();
    }

    @Test
    @DisplayName("una version que no toca nada tambien avanza la version local")
    void unaVersionVaciaTambiénAvanza() {
        when(versionPort.currentVersion()).thenReturn(6L);
        estadoRemoto(7, 6);
        when(changesPort.changes(7L)).thenReturn(cambios(7, List.of()));

        long version = useCase.applyIncremental();

        assertThat(version).isEqualTo(7L);
        verify(replicaPort, never()).upsert(any(), any(), any());
        verify(versionPort).recordIncrementalApplied(7L, PUBLICADO);
    }

    @Test
    @DisplayName("si no hay nada pendiente no hace nada")
    void siNoHayNadaPendienteNoHaceNada() {
        when(versionPort.currentVersion()).thenReturn(7L);
        estadoRemoto(7, 7);

        long version = useCase.applyIncremental();

        assertThat(version).isEqualTo(7L);
        verifyNoInteractions(replicaPort);
        verify(changesPort, never()).changes(anyLong());
    }

    @Test
    @DisplayName("relee el estado vigente de cada identificador que cambio")
    void releeElEstadoVigenteDeCadaId() {
        when(versionPort.currentVersion()).thenReturn(3L);
        estadoRemoto(4, 3);
        when(changesPort.changes(4L))
                .thenReturn(new CatalogChanges(4, List.of(1L), List.of(11L), List.of(21L)));
        when(changesPort.professionalCategories(anyCollection())).thenReturn(List.of());
        when(changesPort.professionals(anyCollection())).thenReturn(List.of());
        when(changesPort.weeklySchedules(anyCollection())).thenReturn(List.of());

        useCase.applyIncremental();

        verify(changesPort).professionalCategories(List.of(1L));
        verify(changesPort).professionals(List.of(11L));
        verify(changesPort).weeklySchedules(List.of(21L));
        verify(replicaPort).upsert(List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("las bajas logicas se escriben como están: deshabilitadas, no borradas")
    void lasBajasLogicasNoBorran() {
        when(versionPort.currentVersion()).thenReturn(3L);
        estadoRemoto(4, 3);
        when(changesPort.changes(4L)).thenReturn(cambios(4, List.of(11L)));
        Professional deshabilitado = new Professional(
                11L, 1L, "Ana", "Perez", false, PUBLICADO, PUBLICADO);
        when(changesPort.professionals(anyCollection())).thenReturn(List.of(deshabilitado));

        useCase.applyIncremental();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Professional>> captor = ArgumentCaptor.forClass(List.class);
        verify(replicaPort).upsert(any(), captor.capture(), any());

        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().enabled()).isFalse();
    }

    @Test
    @DisplayName("repetir la misma version no cambia el resultado")
    void repetirLaVersionNoCambiaElResultado() {
        when(versionPort.currentVersion()).thenReturn(3L);
        estadoRemoto(4, 3);
        when(changesPort.changes(4L)).thenReturn(cambios(4, List.of(11L)));
        when(changesPort.professionals(anyCollection())).thenReturn(List.of());

        useCase.applyIncremental();
        useCase.applyIncremental();

        // La segunda vuelta vuelve a leer los cambios, pero el registro de la versión es
        // idempotente: el que decide si se inserta es el puerto, no este caso de uso.
        verify(changesPort, times(2)).changes(4L);
        verify(versionPort, times(2)).recordIncrementalApplied(eq(4L), eq(PUBLICADO));
    }
}
