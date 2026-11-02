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
 *   <li>{@code catedra/}: {@code CatedraTokenSource}, la única caché de JWT técnico de la
 *       aplicación. Vive acá y no en un contexto porque los dos —{@code sync} y {@code
 *       catalog}— hablan con la misma cuenta técnica; dos cachés serían dos logins con
 *       vigencias que podrían divergir.</li>
 * </ul>
 *
 * <p>Lo que sí es de un contexto no viene acá: los clientes REST de la cátedra viven en
 * {@code sync/infrastructure/catedra} (autenticación) y en {@code catalog/infrastructure/catedra}
 * (snapshot y lector Redis). Este paquete aporta lo que ambos necesitan en común, no los
 * clientes.
 */
