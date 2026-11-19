package com.bartolomeo.clinics.sync.sync.infrastructure.jobs;

import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncIncrementalUseCase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El disparador de la sincronización: al arrancar y cada tanto.
 *
 * <p><strong>Kafka avisa, pero no es la única forma de enterarse.</strong> El aviso es
 * notificación, no fuente de datos: si se pierde uno —una desconexión, un broker que se cae
 * mientras nadie mira—, la copia local se queda atrás sin que nadie lo note. Por eso hay un
 * disparador propio: al arrancar (que es cuando la base local está vacía y hay que pedir el
 * snapshot) y cada tanto (que es lo que recupera el atraso).
 *
 * <p><strong>La comparación de versiones es la que repara.</strong> El disparador no sabe qué
 * falta: llama a la sincronización incremental, que compara la versión local con la que hay
 * disponible y aplica lo que falte. Si la ventana ya no alcanza, reconstruye.
 *
 * <p><strong>Que ya haya una sincronización en curso no es un error.</strong> El lock
 * distribuido está para eso: el disparo se aparta y avisa, y el otro sigue.
 */
@Component
@RequiredArgsConstructor
public class CatalogSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncJob.class);

    private final SyncIncrementalUseCase incrementalSync;

    /**
     * Al arrancar: la base local puede estar vacía y hay que llenarla.
     *
     * <p>Con la base vacía la versión local es cero, que es menor que la más vieja
     * disponible: la sincronización incremental lo detecta y pide el snapshot.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        sincronizar("arranque");
    }

    /**
     * Cada tanto: recupera el atraso si se perdió un aviso.
     *
     * <p>El intervalo sale de {@code clinics.sync.interval-ms}. Con el aviso de Kafka y este
     * barrido juntos, perder una notificación deja de ser un problema: la siguiente
     * comparación de versiones la recupera.
     */
    @Scheduled(fixedDelayString = "${clinics.sync.interval-ms:300000}")
    public void barrer() {
        sincronizar("periódico");
    }

    private void sincronizar(String motivo) {
        try {
            long version = incrementalSync.applyIncremental();
            if (version > 0) {
                log.info("Sincronización {}: la versión local quedó en {}", motivo, version);
            }
        } catch (RuntimeException ex) {
            // El barrido no es crítico: el próximo intento, o un aviso de Kafka, reintenta.
            // Lo que no puede es romper el arranque del servicio.
            log.warn("La sincronización {} no pudo completarse: {}", motivo, ex.getMessage());
        }
    }
}
