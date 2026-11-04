package com.bartolomeo.clinics.sync.sync.domain.ports.out;

import java.time.Instant;

/**
 * Puerto de salida por el que el contexto {@code sync} registra qué versión del catálogo quedó
 * aplicada en la copia local (tabla {@code catalog_version}).
 *
 * <p><strong>La versión y los datos tienen que cambiar juntos.</strong> El enunciado §6.1 pide
 * registrar <em>"exactamente la version que corresponde a los datos almacenados"</em>, así que
 * este puerto no se invoca solo: se invoca dentro de la misma transacción que reemplaza la
 * réplica, y es esa transacción la que hace que las dos cosas sean inseparables. Llamarlo desde
 * afuera de esa transacción rompería la garantía, y el javadoc lo dice para que no se le ocurra
 * a nadie en el futuro.
 *
 * <p><strong>Registrar es idempotente.</strong> Repetir la misma versión no cambia el estado:
 * si ya estaba, no se vuelve a insertar ni se le mueve la fecha. Una operación repetida por
 * timeout (enunciado §8) tiene que producir exactamente el mismo resultado que la primera.
 *
 * <p>El historial es por versión y no un singleton a propósito: guardar solo la última perdería
 * la capacidad de detectar una discontinuidad (SYNC-M1-04), que necesita ver qué ventana se
 * aplicó.
 */
public interface CatalogVersionPort {

    /**
     * La versión local: la más alta que se aplicó y quedó registrada.
     *
     * <p><strong>Cero cuando no hay ninguna.</strong> Una base recién vacía no tiene versión
     * local, y cero es lo que hace que la sincronización incremental pida un snapshot en vez
     * de intentar encadenar desde la nada.
     *
     * @return la versión local, o cero si todavía no se aplicó ninguna
     */
    long currentVersion();

    /**
     * Registra que la versión {@code version} del snapshot quedó aplicada como versión local.
     *
     * <p>No hace nada si la versión ya estaba registrada.
     *
     * @param version            versión del snapshot aplicada, mayor que cero
     * @param snapshotGeneratedAt cuándo generó la cátedra ese snapshot, o {@code null} si no vino
     * @throws RuntimeException si la base no acepta el registro; en ese caso se revierte también
     *         lo que se había aplicado, porque van en la misma transacción
     */
    void recordSnapshotApplied(long version, Instant snapshotGeneratedAt);

    /**
     * Registra que la versión {@code version} quedó aplicada de forma incremental.
     *
     * <p><strong>La versión y los cambios van en la misma transacción.</strong> El enunciado
     * §6.2 dice que la versión local solo puede avanzar cuando la unidad de trabajo terminó
     * correctamente: si el registro de la versión se confirmara aparte de los cambios, un
     * fallo a mitad de una versión dejaría la versión local adelantada y el catálogo atrás, y
     * de ese estado no se sale sin otro snapshot.
     *
     * <p>Es idempotente, como el del snapshot: repetir la misma versión no cambia el estado.
     *
     * @param version    versión aplicada, mayor que cero
     * @param publishedAt cuándo la publicó la cátedra, o {@code null} si no vino
     * @throws RuntimeException si la base no acepta el registro; en ese caso se revierte también
     *         lo que se había aplicado, porque van en la misma transacción
     */
    void recordIncrementalApplied(long version, Instant publishedAt);
}
