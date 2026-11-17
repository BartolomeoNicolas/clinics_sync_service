package com.bartolomeo.clinics.sync.shared;

/**
 * Preocupaciones de la aplicación entera: no es un bounded context.
 *
 * <p>El modelo hexagonal divide el servicio en contexts, y cada uno tiene su
 * {@code domain/application/infrastructure}. Pero hay clases que no pertenecen a ninguno porque
 * atienden a todos: {@code GlobalExceptionHandler} responde con problem+json a los controllers de
 * {@code catalog} y de {@code sync}, y la cadena de filtros valida el JWT de cualquier endpoint.
 *
 * <p>El ejemplo de la catedra no tiene este nivel porque trabajaba con un solo bounded context,
 * así que nunca tuvo que responder dónde va lo transversal. Meter estas clases en un contexto
 * inventaría un dueño falso: {@code SecurityConfig} no es asunto de {@code catalog} ni de
 * {@code sync}. Se documenta como decisión de arquitectura (ver ADR) en vez de forzarlas a un
 * contexto donde no quedarían bien.
 *
 * <p>Regla: si una clase es una sola por aplicación y sirve a todos los contexts, va acá. Si
 * implementa un port de un contexto o traduce su modelo, va al contexto.
 */
