package com.bartolomeo.clinics.sync.sync.domain.ports.out;

/**
 * Los eventos de la cátedra que ya se procesaron.
 *
 * <p><strong>Es la idempotencia, guardada.</strong> Kafka entrega al menos una vez, así que el
 * mismo aviso puede llegar dos veces y la segunda tiene que ser inocua. La clave es el
 * {@code eventId}, que el contrato define como la clave de idempotencia funcional.
 *
 * <p>Habla de eventos ya procesados: no conoce entidades ni JPA.
 */
public interface ProcessedEventRepository {

    /**
     * Si el evento ya se procesó alguna vez.
     *
     * @param eventId identificador del evento
     * @return {@code true} si ya está en la lista
     */
    boolean existsByEventId(String eventId);

    /**
     * Anota el evento como procesado.
     *
     * @param eventId        identificador del evento
     * @param schemaVersion  versión del contrato con la que se escribió
     * @param appliedVersion versión del catálogo que quedó aplicada
     */
    void save(String eventId, int schemaVersion, Long appliedVersion);
}
