package com.bartolomeo.clinics.sync.sync.domain.ports.out;

import java.time.Duration;

/**
 * El lock distribuido de la sincronización.
 *
 * <p><strong>Es lo que impide que dos sincronizaciones se pisen.</strong> El aviso de Kafka
 * puede llegar dos veces, o dos avisos seguidos, y el barrido puede coincidir con un aviso:
 * sin un lock, dos procesos aplicarían cambios a la vez sobre la misma copia local.
 *
 * <p><strong>El lock vive en el namespace propio, no en el de la cátedra.</strong> Es
 * {@code alumnos:{groupId}:locks:sync}: la cátedra no lo lee ni lo escribe, y por eso no se
 * le pide permiso para nada que no sea leer.
 *
 * <p><strong>Se toma con vencimiento.</strong> Un lock sin vencimiento queda tomado para
 * siempre si el proceso que lo tomó se muere, y la sincronización se queda trabada sin que
 * nadie pueda hacer nada. El vencimiento es la red de seguridad: si el proceso muere, el
 * lock se libera solo.
 */
public interface SyncLockPort {

    /**
     * Intenta tomar el lock.
     *
     * @param ttl cuánto vive el lock si nadie lo suelta
     * @return {@code true} si se tomó, {@code false} si ya había otro
     */
    boolean tryAcquire(Duration ttl);

    /**
     * Suelta el lock, si es de este proceso.
     *
     * <p>Se suelta solo si el dueño es el mismo: si el lock venció y otro proceso lo tomó,
     * soltarlo sería robarle el lock a otro.
     */
    void release();
}
