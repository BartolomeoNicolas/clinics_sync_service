package com.bartolomeo.clinics.sync.sync.application.exception;

import com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException;
import com.bartolomeo.clinics.sync.shared.application.exception.ErrorCode;

/**
 * Ya hay una sincronización en curso.
 *
 * <p><strong>No es un fallo: es que no hay que hacer nada todavía.</strong> El aviso de Kafka
 * puede llegar dos veces, o coincidir con un barrido, y en ese caso lo correcto es que la
 * segunda sincronización no empiece: la que está corriendo va a dejar la copia al día igual.
 *
 * <p>Se traduce a 409 con el código estable {@code conflict}: el pedido era válido, pero el
 * mundo está en otro estado.
 */
public class SyncAlreadyRunningException extends ApplicationException {

    public SyncAlreadyRunningException() {
        super(ErrorCode.CONFLICT, "Ya hay una sincronización en curso.");
    }
}
