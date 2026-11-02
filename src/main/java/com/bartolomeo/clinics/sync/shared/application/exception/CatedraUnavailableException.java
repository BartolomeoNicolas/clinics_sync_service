package com.bartolomeo.clinics.sync.shared.application.exception;

/**
 * La cátedra no respondió, o respondió algo que no se pudo entender.
 *
 * <p>A diferencia de {@link CatedraRejectedException}, este fallo <em>puede</em> ser transitorio:
 * la cátedra está caída, hubo un timeout, o devolvió un cuerpo que no matchea el contrato. Un
 * reintento tiene sentido, aunque no garantiza nada.
 *
 * <p>El mensaje nunca incluye el cuerpo de la respuesta de la cátedra: puede traer cualquier
 * cosa y eso no tiene que terminar en un log ni en una respuesta para el cliente.
 *
 * @see ErrorCode#CATEDRA_UNAVAILABLE
 */
public final class CatedraUnavailableException extends ApplicationException {

    public CatedraUnavailableException(String message) {
        super(ErrorCode.CATEDRA_UNAVAILABLE, message);
    }

    public CatedraUnavailableException(String message, Throwable cause) {
        super(ErrorCode.CATEDRA_UNAVAILABLE, message, cause);
    }
}
