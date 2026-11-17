package com.bartolomeo.clinics.sync.sync.domain.ports.out;

import java.time.Instant;
import java.util.Optional;

/**
 * El registro de la última sincronización.
 *
 * <p><strong>Vive en el namespace propio de Redis, no en la base.</strong> Es el estado de
 * fonctionnement del servicio: se escribe al terminar cada sincronización y se consulta para
 * saber cómo terminó la última. No es información de negocio del catálogo —eso está en la
 * copia local— sino el estado del proceso que la mantiene al día.
 */
public interface SyncDiagnosticsPort {

    /**
     * Anota cómo terminó la última sincronización.
     *
     * @param version  versión del catálogo a la que llegó
     * @param outcome  cómo terminó: {@code OK} o {@code ERROR}
     * @param finishedAt cuándo terminó
     * @param detail   detalle del resultado, en texto libre
     */
    void recordLastSync(long version, String outcome, Instant finishedAt, String detail);

    /**
     * Lo que quedó registrado de la última sincronización.
     *
     * @return el registro, o vacío si todavía no terminó ninguna
     */
    Optional<LastSync> lastSync();

    /**
     * Si hay una sincronización en curso.
     *
     * @return {@code true} si el lock está tomado
     */
    boolean isRunning();

    /**
     * La última sincronización registrada.
     *
     * @param version     versión del catálogo a la que llegó
     * @param outcome     cómo terminó
     * @param finishedAt  cuándo terminó
     * @param detail      detalle del resultado
     */
    record LastSync(long version, String outcome, Instant finishedAt, String detail) {
    }
}
