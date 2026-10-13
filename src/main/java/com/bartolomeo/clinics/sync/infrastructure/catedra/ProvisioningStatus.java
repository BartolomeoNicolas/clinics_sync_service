package com.bartolomeo.clinics.sync.infrastructure.catedra;

/**
 * Estado de aprovisionamiento de la cuenta técnica de la cátedra.
 *
 * <p>Refleja el campo {@code provisioningStatus} de la respuesta de
 * {@code POST /api/student/register} y de {@code GET /api/student/integration}.
 *
 * <p>La cuenta técnica se registra <strong>una sola vez</strong> (enunciado §9). Antes de
 * registrarla, el servicio tiene que poder arrancar igual: por eso {@link #PENDING} es el
 * estado inicial y no un error.
 */
public enum ProvisioningStatus {

    /** Todavía no se registró la cuenta técnica. Estado inicial. */
    PENDING,

    /** La cátedra entregó credenciales y configuración. Se puede integrar. */
    PROVISIONED,

    /** El registro falló del lado de la cátedra. */
    FAILED,

    /** La cuenta fue revocada. */
    REVOKED
}