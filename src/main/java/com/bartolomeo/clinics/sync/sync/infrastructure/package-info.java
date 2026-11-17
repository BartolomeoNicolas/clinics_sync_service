package com.bartolomeo.clinics.sync.sync.infrastructure;

/**
 * Infraestructura del contexto {@code sync}.
 *
 * <p>Además de {@code persistence/} y {@code web/}, el contexto {@code sync} necesita:
 * <ul>
 *   <li>{@code persistence/}: {@code entity} (con anotaciones JPA), {@code repository}
 *       (Spring Data) y {@code adapter}. Vive acá lo que es estado de sincronización y no
 *       réplica del catálogo: la versión aplicada, los eventos ya procesados y los errores.
 *       La réplica la persiste el contexto {@code catalog}, que es la dueña de esos datos.</li>
 *   <li>{@code messaging/}: consumidores de {@code CatalogUpdated} y de la cátedra.</li>
 *   <li>{@code catedra/}: cliente de autenticación técnico y llamadores de la cátedra.</li>
 * </ul>
 *
 * <p>Los consumidores invocan los ports {@code in} directamente, sin pasar por la fachada: un
 * listener no es una fachada de servicio (desviación D-4 del plan maestro).
 */
