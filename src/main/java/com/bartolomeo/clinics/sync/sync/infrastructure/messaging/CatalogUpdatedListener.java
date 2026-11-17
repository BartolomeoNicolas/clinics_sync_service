package com.bartolomeo.clinics.sync.sync.infrastructure.messaging;

import com.bartolomeo.clinics.sync.sync.domain.model.CatalogEvent;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.ProcessCatalogEventUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncErrorPort;
import com.bartolomeo.clinics.sync.sync.infrastructure.messaging.dto.CatalogUpdatedEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Escucha los avisos de versión nueva del catálogo.
 *
 * <p><strong>El offset se confirma después de persistir, nunca antes.</strong> Si se
 * confirmara antes, un corte entre la confirmación y el guardado haría que el aviso se
 * perdiera para siempre: Kafka ya no lo mandaría y el catálogo se quedaría atrasado sin que
 * nadie lo supiera. Por eso la confirmación manual es la última cosa que hace el listener.
 *
 * <p><strong>Lo que no se puede arreglar se registra y se confirma.</strong> Un mensaje
 * ilegible, una versión del contrato que no se conoce: ninguno de esos casos mejora con otro
 * intento, y dejarlo sin confirmar haría que el mismo mensaje volviera a llegar para siempre
 * y trabara la partición.
 *
 * <p><strong>Lo que sí se puede arreglar no se pierde.</strong> Un error de infraestructura se
 * deja pasar hacia el contenedor, que lo reintenta con un tope. El offset no se confirma
 * mientras tanto, así que el mensaje vuelve a llegar cuando el problema se haya resuelto.
 *
 * <p><strong>Perder un aviso no pierde datos.</strong> Kafka solo notifica: lo que se
 * sincroniza se decide comparando la versión local con la que hay disponible. Si un aviso se
 * pierde, el siguiente —o la comprobación de versiones— recupera el atraso.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "clinics.catedra.kafka",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CatalogUpdatedListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogUpdatedListener.class);

    /** De qué falló este mensaje. Valor estable: lo consultan el endpoint de errores. */
    private static final String KIND = "evento";

    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final ProcessCatalogEventUseCase processEvent;
    private final SyncErrorPort errorPort;

    /**
     * Recibe el aviso de que hay una versión nueva del catálogo.
     *
     * @param record         mensaje de Kafka, con su clave y su cuerpo
     * @param acknowledgment confirmación manual del offset
     */
    @KafkaListener(
            topics = "${clinics.catedra.kafka.catalog-topic}",
            groupId = "${clinics.catedra.kafka.consumer-group}",
            containerFactory = "catedraKafkaListenerContainerFactory")
    public void onCatalogUpdated(
            ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {

        CatalogUpdatedEvent evento;
        try {
            evento = objectMapper.readValue(record.value(), CatalogUpdatedEvent.class);
            validar(evento);
        } catch (RuntimeException ex) {
            log.warn("Aviso de la cátedra irrecuperable: {}", ex.getMessage());
            registrarError(record, ex);
            acknowledgment.acknowledge();
            return;
        }

        CatalogEvent paraElDominio = new CatalogEvent(
                evento.eventId(), evento.newVersion(), evento.schemaVersion());

        try {
            processEvent.process(paraElDominio);
        } catch (RuntimeException ex) {
            // Esto sí se puede arreglar: se deja pasar hacia el contenedor, que reintenta
            // con un tope. El offset no se confirma, así que el mensaje vuelve a llegar.
            log.error("Falla recuperable al procesar el aviso {}: {}",
                    evento.eventId(), ex.getMessage());
            throw ex;
        }

        acknowledgment.acknowledge();
    }

    /**
     * Lo que tiene que traer el aviso para que se pueda entender.
     *
     * <p>Un aviso sin identificador no tiene idempotencia y uno sin versión no dice qué hay
     * que sincronizar: los dos son irrecuperables, y por eso se rechazan antes de tocar nada.
     */
    private void validar(CatalogUpdatedEvent evento) {
        if (evento == null) {
            throw new IllegalArgumentException("El aviso de la cátedra vino vacío.");
        }
        if (evento.eventId() == null || evento.eventId().isBlank()) {
            throw new IllegalArgumentException(
                    "El aviso de la cátedra no tiene eventId: sin él no hay idempotencia.");
        }
        if (evento.newVersion() == null) {
            throw new IllegalArgumentException(
                    "El aviso de la cátedra no dice qué versión hay disponible.");
        }
        if (evento.schemaVersion() == null || evento.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "El aviso de la cátedra trae la versión de contrato "
                            + evento.schemaVersion() + " y este servicio entiende la "
                            + SCHEMA_VERSION + ".");
        }
    }

    /**
     * Anota el mensaje como irrecuperable.
     *
     * <p>El identificador se saca del mensaje best effort: si el mensaje no se pudo leer, no
     * hay identificador, y lo que queda es el texto del error.
     */
    private void registrarError(ConsumerRecord<String, String> record, RuntimeException causa) {
        Long version = null;
        try {
            CatalogUpdatedEvent evento =
                    objectMapper.readValue(record.value(), CatalogUpdatedEvent.class);
            version = evento.newVersion();
        } catch (RuntimeException ex) {
            log.debug("No se pudo leer el mensaje fallido para sacarle la versión");
        }

        try {
            errorPort.record(com.bartolomeo.clinics.sync.sync.domain.model.SyncError.now(
                    KIND, version, mensajeDe(causa), trazaDe(causa)));
        } catch (RuntimeException sinRegistro) {
            log.warn(
                    "No se pudo registrar el aviso irrecuperable en sync_error; "
                            + "se conserva el fallo original.",
                    sinRegistro);
        }
    }

    private static String mensajeDe(RuntimeException failure) {
        String mensaje = failure.getMessage();
        return mensaje == null || mensaje.isBlank() ? failure.getClass().getName() : mensaje;
    }

    private static String trazaDe(RuntimeException failure) {
        StringWriter buffer = new StringWriter();
        failure.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }
}
