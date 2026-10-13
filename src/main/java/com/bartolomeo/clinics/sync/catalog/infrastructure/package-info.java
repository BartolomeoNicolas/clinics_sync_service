package com.bartolomeo.clinics.sync.catalog.infrastructure;

/**
 * Infraestructura del contexto {@code catalog}: toda la tecnología vive acá.
 *
 * <ul>
 *   <li>{@code persistence/}: {@code entity} (con anotaciones JPA), {@code repository}
 *       (Spring Data), {@code adapter} (implementa el port {@code out}) y {@code mapper}
 *       (domain ↔ entity).</li>
 *   <li>{@code web/}: controllers, DTOs con {@code jakarta.validation} y su mapper.</li>
 *   <li>{@code catedra/}: cliente REST y lector Redis del catálogo de la cátedra.</li>
 * </ul>
 *
 * <p>Ningún adapter expone tipos de su tecnología hacia adentro: se traduce a y desde el
 * modelo de dominio.
 */
