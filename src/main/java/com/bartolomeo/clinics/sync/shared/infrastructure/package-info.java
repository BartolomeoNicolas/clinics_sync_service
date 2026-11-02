package com.bartolomeo.clinics.sync.shared.infrastructure;

/**
 * Infraestructura de la aplicación entera: una sola por servicio.
 *
 * <ul>
 *   <li>{@code config/}: {@code LocalProperties} y {@code RequiredSecretsInitializer}, que
 *       validan los secretos al arrancar antes de que cualquier contexto los necesite.</li>
 *   <li>{@code security/}: la cadena de filtros, la validación del JWT y los handlers que
 *       responden 401/403 en problem+json. Protege los endpoints de todos los contexts.</li>
 *   <li>{@code web/exception/}: {@code @RestControllerAdvice}, el body problem+json y el
 *       mapper de code a status. Sin esto cada controller resolvería el error a su manera.</li>
 * </ul>
 *
 * <p>Lo que sí es de un contexto no viene acá: el cliente REST de la autenticación técnica vive
 * en {@code sync/infrastructure/catedra}, y el de snapshot en {@code catalog/infrastructure/catedra}.
 */
