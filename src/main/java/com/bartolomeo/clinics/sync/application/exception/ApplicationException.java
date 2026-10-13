package com.bartolomeo.clinics.sync.application.exception;

/**
 * Excepción base de la capa de aplicación.
 *
 * <p>Nace en {@code application/exception}, sin dependencias de Spring ni de web: es una
 * regla del negocio, no un detalle del transporte. Quien la traduce a una respuesta HTTP es
 * {@code infrastructure/web/exception.GlobalExceptionHandler}.
 *
 * <p>Cada subclase lleva un {ErrorCode} fijo, y ese código es parte del contrato:
 * el mismo tipo de fallo devuelve siempre el mismo {@code code}, para que el cliente decida
 * por {@code status} + {@code code} y nunca por el texto de {@code detail}.
 *
 * @see com.bartolomeo.clinics.sync.infrastructure.web.exception.ErrorStatusMapper
 */
public abstract class ApplicationException extends RuntimeException {

    private final transient ErrorCode errorCode;

    protected ApplicationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ApplicationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}