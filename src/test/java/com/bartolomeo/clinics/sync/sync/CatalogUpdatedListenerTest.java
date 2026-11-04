package com.bartolomeo.clinics.sync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bartolomeo.clinics.sync.sync.domain.ports.in.ProcessCatalogEventUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncErrorPort;
import com.bartolomeo.clinics.sync.sync.infrastructure.messaging.CatalogUpdatedListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

/**
 * El listener de los avisos de versión nueva.
 *
 * <p>Lo que se prueba es la disciplina del offset: se confirma después de persistir, lo que no
 * se puede arreglar se registra y se confirma, y lo que sí se puede arreglar no se confirma
 * para que el mensaje vuelva a llegar.
 */
@DisplayName("el listener de avisos de catalogo")
class CatalogUpdatedListenerTest {

    private static final String TOPIC = "catedra.catalog.nicobartok0-prog2";

    private final ProcessCatalogEventUseCase processEvent = mock(ProcessCatalogEventUseCase.class);
    private final SyncErrorPort errorPort = mock(SyncErrorPort.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);

    private CatalogUpdatedListener listener;

    @BeforeEach
    void preparar() {
        listener = new CatalogUpdatedListener(
                new ObjectMapper(), processEvent, errorPort);
    }

    private ConsumerRecord<String, String> mensaje(String cuerpo) {
        return new ConsumerRecord<>(TOPIC, 0, 0L, "7", cuerpo);
    }

    private static String avisoValido() {
        return """
                {
                  "eventId": "catalog-event-7-4f3b",
                  "eventType": "CatalogUpdated",
                  "newVersion": 7,
                  "occurredAt": "2026-09-10T13:44:59Z",
                  "source": "MANUAL",
                  "schemaVersion": 1
                }
                """;
    }

    @Test
    @DisplayName("procesa el aviso y confirma el offset despues de persistir")
    void procesaYConfirmaDespues() {
        listener.onCatalogUpdated(mensaje(avisoValido()), acknowledgment);

        InOrder orden = inOrder(processEvent, acknowledgment);
        orden.verify(processEvent).process(any());
        orden.verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("los campos desconocidos se ignoran")
    void losCamposDesconocidosSeIgnoran() {
        String cuerpo = """
                {
                  "eventId": "catalog-event-8",
                  "eventType": "CatalogUpdated",
                  "newVersion": 8,
                  "schemaVersion": 1,
                  "campoDelFuturo": "lo que sea"
                }
                """;

        listener.onCatalogUpdated(mensaje(cuerpo), acknowledgment);

        verify(processEvent).process(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("un mensaje ilegible se registra y se confirma")
    void unMensajeIlegibleSeRegistra() {
        listener.onCatalogUpdated(mensaje("{ esto no es json"), acknowledgment);

        verify(processEvent, never()).process(any());
        verify(errorPort).record(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("un mensaje sin eventId se registra y se confirma")
    void unMensajeSinIdentificadorSeRegistra() {
        String cuerpo = """
                {"eventType": "CatalogUpdated", "newVersion": 7, "schemaVersion": 1}
                """;

        listener.onCatalogUpdated(mensaje(cuerpo), acknowledgment);

        verify(processEvent, never()).process(any());
        verify(errorPort).record(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("una version de contrato desconocida se registra y se confirma")
    void unaVersionDesconocidaSeRegistra() {
        String cuerpo = """
                {"eventId": "evento-2", "eventType": "CatalogUpdated", "newVersion": 7,
                 "schemaVersion": 2}
                """;

        listener.onCatalogUpdated(mensaje(cuerpo), acknowledgment);

        verify(processEvent, never()).process(any());
        verify(errorPort).record(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("un error recuperable no confirma el offset: el mensaje vuelve a llegar")
    void unErrorRecuperableNoConfirmaElOffset() {
        doThrow(new DataAccessResourceFailureException("la base esta caida"))
                .when(processEvent).process(any());

        var error = catchThrowableOfType(
                DataAccessResourceFailureException.class,
                () -> listener.onCatalogUpdated(mensaje(avisoValido()), acknowledgment));

        assertThat(error).isNotNull();
        verify(acknowledgment, never()).acknowledge();
        verify(errorPort, never()).record(any());
    }
}
