package com.bartolomeo.clinics.sync.sync.domain.ports.out;

import com.bartolomeo.clinics.sync.sync.domain.model.SyncError;

/**
 * Puerto de salida por el que quedan registrados los intentos de sincronización que fallaron
 * (tabla {@code sync_error}).
 *
 * <p><strong>Se registra en una transacción propia, deliberadamente.</strong> El error tiene que
 * sobrevivir al rollback de la sincronización que falló: si corriera en la misma transacción,
 * se perdería justo cuando más hace falta —un fallo que no deja rastro es el peor de los
 * posibles—. Por eso {@code record} usa propagación {@code REQUIRES_NEW} en su adaptador: se
 * confirma solo, aunque la transacción que lo llamó después se revierta entera.
 *
 * <p>Registrar el error es secundario respecto del fallo original. Si tampoco se puede escribir
 * —porque la base está caída, que suele ser justamente la causa—, el error original tiene que
 * seguir siendo el que sale afuera; el llamador atrapa esa excepción y la registra sin dejar
 * que la del registro la tape.
 *
 * <p>Los errores quedan consultables sin tocar la base por otros medios (SYNC-M1-07), que es lo
 * que pide el enunciado §4.1.
 */
public interface SyncErrorPort {

    /**
     * Registra un intento fallido.
     *
     * <p>Si tampoco se pudo escribir, la excepción sale: taparla aquí escondería que la
     * bitácora también está rota. Quien llama es el caso de uso, y él es el que atrapa para
     * que el error del registro no tape al original que motivó la llamada.
     *
     * @param error el registro a guardar, con su {@code occurredAt} ya puesto. Nunca
     *              {@code null}
     * @throws RuntimeException si la base no acepta el registro
     */
    void record(SyncError error);

    /**
     * Los errores más recientes, del más nuevo al más viejo.
     *
     * <p>Es lo que consulta el endpoint de errores: quien pregunta el estado del servicio no
     * necesita credenciales de base ni saber el esquema.
     *
     * @return los errores registrados, como mucho los últimos cien
     */
    java.util.List<SyncError> recent();
}
