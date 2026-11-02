package com.bartolomeo.clinics.sync.shared.application;

/**
 * Excepciones de aplicación compartidas por todos los contexts.
 *
 * <p>{@code ApplicationException} y {@code ErrorCode} son la base sobre la que cada contexto
 * define las suyas: los codes entran en el body problem+json y son parte del contrato, no un
 * detalle de implementación.
 *
 * <p>También viven acá {@code CatedraRejectedException} y {@code CatedraUnavailableException},
 * porque las tira el adapter de autenticación del contexto {@code sync} y las va a tirar también
 * el adapter de snapshot del contexto {@code catalog}: las dos llamadas hablan con la misma
 * catedra y comparten el mismo code. Duplicarlas por contexto haría que un mismo
 * {@code catedra-rejected} se escriba dos veces.
 */
