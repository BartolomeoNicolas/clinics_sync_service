package com.bartolomeo.clinics.sync.sync.domain.ports.in;

import com.bartolomeo.clinics.sync.sync.domain.model.CatalogEvent;

/**
 * Procesa la notificación de que hay una versión nueva del catálogo.
 */
public interface ProcessCatalogEventUseCase {

    /**
     * Sincroniza lo que haya pendiente a partir de la versión local.
     *
     * <p><strong>El mismo {@code eventId} dos veces no repite el efecto.</strong> La entrega
     * de Kafka es al menos una vez y el mismo aviso puede llegar repetido: la segunda vez no
     * vuelve a sincronizar.
     *
     * <p><strong>El aviso no dice qué hay que hacer.</strong> Solo dice que hay una versión
     * disponible; lo que se aplique se decide comparando la versión local con la que hay en
     * Redis. Por eso, perder un aviso no pierde datos: el siguiente, o la comprobación de
     * versiones, recupera el atraso.
     *
     * @param event evento de la cátedra, ya traducido al idioma del dominio
     */
    void process(CatalogEvent event);
}
