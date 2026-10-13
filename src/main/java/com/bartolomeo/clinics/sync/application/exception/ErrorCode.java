package com.bartolomeo.clinics.sync.application.exception;

/**
 * Identidad estable de cada tipo de error.
 *
 * <p>Vive en {@code application}, no en {@code infrastructure}, por una razón concreta: el
 * código identifica <em>qué pasó</em>, y eso lo define el negocio. El status HTTP con el que
 * se responde es una decisión de infraestructura, y no puede vivificar el dominio hacia
 * adentro —{@code application} no depende de {@code infrastructure}, nunca al revés.
 *
 * <p>El mapeo código → status está en
 * {@code infrastructure.web.exception.ErrorStatusMapper}.
 *
 * <p><strong>El valor de {@link #code} es contrato.</strong> El anexo §13 dice que los
 * clientes deciden por {@code status} + {@code code} y nunca por el texto de {@code detail}.
 * Para que eso sea cierto, el mismo tipo de fallo tiene que devolver siempre el mismo código:
 * con cadenas libres cada quien escribe la que se le ocurre y el cliente termina comparando
 * texto. No se renombra un código; se agrega uno nuevo.
 */
public enum ErrorCode {

    // --- 400 ---------------------------------------------------------------
    /** Faltan campos o tienen un formato inválido. El detalle va en `fieldErrors`. */
    VALIDATION_ERROR("validation-error", "Solicitud inválida"),

    // --- 401 ---------------------------------------------------------------
    /** No venía token, o el que venía no se pudo validar (firma o audiencia). */
    UNAUTHENTICATED("unauthenticated", "No autenticado"),

    /** El token es válido pero expiró. Se distingue del anterior porque el cliente renueva
     *  en un caso y tiene que volver a autenticarse en el otro. */
    TOKEN_EXPIRED("token-expired", "Token expirado"),

    // --- 403 ---------------------------------------------------------------
    /** Autenticado pero sin permiso sobre el recurso. */
    FORBIDDEN("forbidden", "Sin permiso"),

    // --- 404 ---------------------------------------------------------------
    NOT_FOUND("not-found", "No encontrado"),

    // --- 409 ---------------------------------------------------------------
    CONFLICT("conflict", "Conflicto de estado"),

    // --- 500 ---------------------------------------------------------------
    INTERNAL_ERROR("internal-error", "Error interno"),

    // --- 502 / 503 ---------------------------------------------------------
    /** La cátedra no respondió, o respondió algo que no se pudo entender. */
    CATEDRA_UNAVAILABLE("catedra-unavailable", "Servicio de la cátedra no disponible"),

    /** El catálogo local no está en un estado que permita responder. */
    CATALOG_UNAVAILABLE("catalog-unavailable", "Catálogo no disponible"),

    /** La versión local no conecta con la ventana disponible: hay que reconstruir. El cliente
     *  debería reintentar más adelante, no cambiar nada. */
    SYNC_DISCONTINUITY("sync-discontinuity", "Sincronización descontinuada");

    private final String code;
    private final String title;

    ErrorCode(String code, String title) {
        this.code = code;
        this.title = title;
    }

    /** Cadena estable que viaja en el JSON. No cambiar sin actualizar el contrato. */
    public String getCode() {
        return code;
    }

    /** Texto corto y estable. No es el mensaje del error: ese va en `detail`. */
    public String getTitle() {
        return title;
    }

    /** URI de documentación del código, para que un cliente pueda consultarla. */
    public String getType() {
        return "https://dqmdz-um.github.io/prog2/clinics/problems/" + code;
    }
}