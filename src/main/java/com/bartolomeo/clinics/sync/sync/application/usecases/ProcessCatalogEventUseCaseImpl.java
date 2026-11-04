package com.bartolomeo.clinics.sync.sync.application.usecases;

import com.bartolomeo.clinics.sync.sync.domain.model.CatalogEvent;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.ProcessCatalogEventUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncIncrementalUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Procesa la notificación de que hay una versión nueva del catálogo.
 *
 * <p><strong>El aviso dispara la sincronización, no la reemplaza.</strong> Lo que se aplique
 * lo decide la comparación entre la versión local y la que hay disponible en Redis: el aviso
 * solo dice que hay algo nuevo. Por eso un aviso perdido no pierde datos —el próximo aviso,
 * o la comprobación de versiones, recupera el atraso— y por eso un aviso repetido tampoco
 * rompe nada: la sincronización compara y ve que no hay nada que hacer.
 *
 * <p><strong>La idempotencia va por el {@code eventId}.</strong> La entrega de Kafka es al
 * menos una vez y el mismo aviso puede llegar dos veces: la segunda vez el evento ya está en
 * la lista de procesados y no se vuelve a sincronizar.
 *
 * <p><strong>El efecto y la anotación del evento van en la misma transacción.</strong> O se
 * sincroniza y se anota, o no se hace ninguna de las dos cosas. Si se anotara antes, una
 * caída entre las dos cosas dejaría el evento marcado como procesado sin haber sincronizado, y
 * nunca volvería a llegar.
 */
@Component
@RequiredArgsConstructor
public class ProcessCatalogEventUseCaseImpl implements ProcessCatalogEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessCatalogEventUseCaseImpl.class);

    private final ProcessedEventRepository processedEvents;
    private final SyncIncrementalUseCase incrementalSync;

    @Override
    @Transactional
    public void process(CatalogEvent event) {

        if (processedEvents.existsByEventId(event.eventId())) {
            // Ya se proceso una vez. Repetir el efecto seria un error, no una precaucion.
            log.debug("El evento {} ya se habia procesado", event.eventId());
            return;
        }

        long aplicada = incrementalSync.applyIncremental();

        processedEvents.save(event.eventId(), event.schemaVersion(), aplicada);

        log.info("Evento {} procesado: la version local quedo en {}", event.eventId(), aplicada);
    }
}
