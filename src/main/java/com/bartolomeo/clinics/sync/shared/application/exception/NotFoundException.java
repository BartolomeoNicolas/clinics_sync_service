package com.bartolomeo.clinics.sync.shared.application.exception;

/**
 * El recurso pedido no existe.
 *
 * <p>Nace en {@code shared} y no en un contexto concreto porque es el mismo 404 para todos:
 * un profesional inexistente en {@code catalog} y una reserva inexistente en {@code booking}
 * son la misma regla de negocio —no hay nada que mostrar— y merecen el mismo código estable
 * {@code not-found}. Meterla en un contexto obligaría a que el otro lo importe, que es justo
 * la flecha que {@code shared} existe para evitar.
 *
 * <p><strong>Se lanza desde la fachada, nunca desde el dominio.</strong> Que un id no exista
 * es un hecho del mundo exterior, no una regla que el modelo invalide. La fachada traduce el
 * {@code Optional} vacío en esto, y {@code GlobalExceptionHandler} lo traduce a 404
 * {@code problem+json} con el código estable.
 */
public class NotFoundException extends ApplicationException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
