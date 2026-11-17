package com.bartolomeo.clinics.sync.sync.domain.ports.in;

/**
 * Aplica los cambios incrementales que van desde la versión local hasta la que hay disponible.
 */
public interface SyncIncrementalUseCase {

    /**
     * Aplica los cambios pendientes, en orden, y devuelve la versión local que quedó.
     *
     * <p>Si hay una discontinuidad —la versión local quedó antes de la ventana disponible— se
     * reconstruye con un snapshot completo en vez de intentar un incremento que no se puede
     * encadenar.
     *
     * @return la versión local después de aplicar
     */
    long applyIncremental();
}
