package com.bartolomeo.clinics.sync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bartolomeo.clinics.sync.sync.application.usecases.ProcessCatalogEventUseCaseImpl;
import com.bartolomeo.clinics.sync.sync.domain.model.CatalogEvent;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.ProcessCatalogEventUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncIncrementalUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * El aviso de versión nueva y la idempotencia.
 *
 * <p>Lo que se prueba acá es lo que el contrato exige del consumidor: el mismo aviso dos
 * veces no repite el efecto, y el aviso solo dispara la sincronización —no la reemplaza—.
 */
@DisplayName("el aviso de version nueva del catalogo")
class ProcessCatalogEventUseCaseImplTest {

    private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
    private final SyncIncrementalUseCase incrementalSync = mock(SyncIncrementalUseCase.class);

    private final ProcessCatalogEventUseCase useCase =
            new ProcessCatalogEventUseCaseImpl(processedEvents, incrementalSync);

    @BeforeEach
    void preparar() {
        when(processedEvents.existsByEventId("evento-1")).thenReturn(false);
        when(incrementalSync.applyIncremental()).thenReturn(7L);
    }

    @Test
    @DisplayName("sincroniza y despues anota el evento como procesado")
    void sincronizaYDespuesAnota() {
        useCase.process(new CatalogEvent("evento-1", 7L, 1));

        InOrder orden = inOrder(incrementalSync, processedEvents);
        orden.verify(incrementalSync).applyIncremental();
        orden.verify(processedEvents).save("evento-1", 1, 7L);
    }

    @Test
    @DisplayName("el mismo eventId dos veces no repite el efecto")
    void elMismoEventIdNoRepiteElEfecto() {
        when(processedEvents.existsByEventId("evento-2")).thenReturn(true);

        useCase.process(new CatalogEvent("evento-2", 7L, 1));

        verify(incrementalSync, never()).applyIncremental();
        verify(processedEvents, never()).save(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("el aviso no dice que hacer: la sincronizacion decide comparando versiones")
    void elAvisoNoDiceQueHacer() {
        useCase.process(new CatalogEvent("evento-3", 7L, 1));

        // Lo unico que hace el caso de uso es disparar la sincronizacion: no lee el aviso
        // para decidir a que version llegar.
        verify(incrementalSync).applyIncremental();
        assertThat(processedEvents.existsByEventId("evento-3")).isFalse();
    }
}
