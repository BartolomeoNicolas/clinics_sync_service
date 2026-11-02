package com.bartolomeo.clinics.sync.catalog.domain.ports.out;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot;

/**
 * Puerto de salida por el que el contexto {@code catalog} obtiene el snapshot completo del
 * catálogo de la cátedra (anexo §7).
 *
 * <p>Es una operación <strong>idempotente de solo lectura</strong>: pedir el mismo snapshot dos
 * veces devuelve lo mismo y no deja efectos. Por eso el adaptador puede reintentar con backoff
 * sin riesgo de duplicar nada — a diferencia de crear un hold, que no se reintenta a la
 * ligera.
 *
 * <p><strong>La operación es unitaria a propósito.</strong> El anexo §7 obliga a que
 * {@code snapshotVersion} solo se guarde cuando las tres colecciones quedaron aplicadas, así
 * que el snapshot se entrega completo y quien lo recibe decide si avanza la versión o se queda
 * como estaba. No hay una forma de pedir "solo las categorías".
 *
 * <p>Devuelve el snapshot con las entidades deshabilitadas incluidas: son bajas lógicas y
 * aplicarlas es parte de sincronizar, no algo que filtrar.
 *
 * <p>Fallos: las excepciones de aplicación con los códigos {@code catedra-rejected} (el token
 * técnico fue rechazado) y {@code catedra-unavailable} (la cátedra no respondió, respondió mal
 * o se quedó sin tiempo). Quien llama no distingue entre "apagada" y "no contesta": las dos
 * dejan la copia local sin cambios.
 *
 * @see com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot
 */
public interface CatalogSnapshotPort {

    /**
     * Pide el snapshot completo a {@code GET /api/synchronization/snapshot}.
     *
     * @return el snapshot vigente, con las tres colecciones nunca {@code null}. Nunca
     *         {@code null}.
     * @throws com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException si
     *         la cátedra rechaza las credenciales o no se puede entender su respuesta
     */
    CatalogSnapshot fetchSnapshot();
}
