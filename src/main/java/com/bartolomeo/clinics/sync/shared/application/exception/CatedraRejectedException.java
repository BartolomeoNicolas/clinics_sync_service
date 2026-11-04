package com.bartolomeo.clinics.sync.shared.application.exception;

/**
 * La cátedra rechazó las credenciales técnicas de esta cuenta (401 en
 * {@code POST /api/authenticate}).
 *
 * <p>Existe como clase aparte y no como {@code CATEDRA_UNAVAILABLE} genérico porque el problema
 * es de <strong>configuración</strong>, no de red: un reintento no lo arregla, hay que corregir
 * {@code CATEDRA_TECHNICAL_USER} y {@code CATEDRA_TECHNICAL_PASSWORD}. Quien implementa un
 * reintento tiene que poder distinguirlo, y para eso alcanza con el catch del tipo.
 *
 * <p>El mensaje no incluye lo que se mandó ni lo que respondió la cátedra: solo apunta a las
 * variables a revisar.
 *
 * @see ErrorCode#CATEDRA_REJECTED
 */
public final class CatedraRejectedException extends ApplicationException {

    public CatedraRejectedException(String message) {
        super(ErrorCode.CATEDRA_REJECTED, message);
    }
}
