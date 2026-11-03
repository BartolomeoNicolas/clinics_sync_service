package com.bartolomeo.clinics.sync.sync.domain.ports.in;

/**
 * Caso de uso de sincronización completa: aplicar un snapshot de la cátedra a la copia local
 * (enunciado §6.1).
 *
 * <p><strong>La operación es atómica y eso es su contrato, no un detalle de implementación.</strong>
 * El anexo §7 obliga a que {@code snapshotVersion} solo se guarde como versión local cuando las
 * tres colecciones quedaron aplicadas, y el enunciado §6.1 agrega que hay que registrar
 * <em>"exactamente la version que corresponde a los datos almacenados"</em>. Un caso de uso que
 * aplicara las colecciones por un lado y la versión por otro podría dejar la versión adelantada
 * con la copia a medias, que es precisamente el estado del que después no hay forma de
 * recuperarse sin detectarlo.
 *
 * <p>Quien lo llama no tiene que preocuparse por deshacer nada: si algo falla, no queda nada
 * aplicado. El fallo sale como excepción y además queda registrado en {@code sync_error}.
 *
 * <p>Aplicar el mismo snapshot dos veces produce el mismo estado que una: repetir una
 * operación por timeout o por una respuesta perdida no duplica datos ni cambia la versión
 * (enunciado §8).
 *
 * <p>Corresponde a la evidencia 1 del §11: <em>"una inicializacion desde snapshot"</em>.
 */
public interface SyncFullSnapshotUseCase {

    /**
     * Pide el snapshot a la cátedra y reconstruye la copia local con él.
     *
     * @return la versión del snapshot que quedó aplicada como versión local. Nunca
     *         {@code null}
     * @throws com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException si
     *         la cátedra no está disponible o rechaza las credenciales
     * @throws RuntimeException si la base no acepta los datos; la transacción se revierte
     *         entera y la copia anterior queda intacta
     */
    long applyFullSnapshot();
}
