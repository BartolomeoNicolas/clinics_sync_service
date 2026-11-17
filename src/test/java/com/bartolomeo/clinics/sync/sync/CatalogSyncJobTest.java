package com.bartolomeo.clinics.sync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncIncrementalUseCase;
import com.bartolomeo.clinics.sync.sync.infrastructure.jobs.CatalogSyncJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El disparador de la sincronización.
 *
 * <p>Lo que se prueba es que el servicio no depende de que llegue un aviso: al arrancar
 * sincroniza igual, y un fallo en el barrido no se lleva por delante el arranque.
 */
@DisplayName("el disparador de la sincronizacion")
class CatalogSyncJobTest {

    private final SyncIncrementalUseCase incremental = mock(SyncIncrementalUseCase.class);
    private final CatalogSyncJob job = new CatalogSyncJob(incremental);

    @Test
    @DisplayName("al arrancar sincroniza, porque la base local puede estar vacia")
    void alArrancarSincroniza() {
        when(incremental.applyIncremental()).thenReturn(324L);

        job.alArrancar();

        verify(incremental).applyIncremental();
    }

    @Test
    @DisplayName("el barrido periodico tambien sincroniza")
    void elBarridoTambienSincroniza() {
        when(incremental.applyIncremental()).thenReturn(325L);

        job.barrer();

        verify(incremental).applyIncremental();
    }

    @Test
    @DisplayName("un fallo en la sincronizacion no se propaga")
    void unFalloNoSePropaga() {
        when(incremental.applyIncremental()).thenThrow(new IllegalStateException("redis caido"));

        var error = catchThrowableOfType(RuntimeException.class, job::alArrancar);

        assertThat(error).isNull();
    }

    @Test
    @DisplayName("que haya otra sincronizacion en curso no es un fallo")
    void otraSincronizacionEnCursoNoEsUnFallo() {
        when(incremental.applyIncremental())
                .thenThrow(new IllegalStateException("ya hay una sincronizacion en curso"));

        var error = catchThrowableOfType(RuntimeException.class, job::barrer);

        assertThat(error).isNull();
    }
}
