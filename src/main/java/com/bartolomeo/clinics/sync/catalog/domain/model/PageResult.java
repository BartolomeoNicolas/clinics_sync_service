package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.util.List;

/**
 * Una página de resultados, sin depender de Spring.
 *
 * <p><strong>Existe porque el dominio no puede nombrar a {@code org.springframework.data}.</strong>
 * El puerto {@code in} devuelve esto y no {@code Page}: la regla del paquete {@code domain}
 * prohíbe importar Spring, y una página es exactamente ese tipo de detalle de infraestructura.
 * El adaptador traduce; el dominio solo dice qué es una página.
 *
 * <p>Los campos son los que cualquier pantalla necesita para paginar sin adivinar: dónde está
 * lo que se está viendo, cuánto hay en total y cuántas páginas da.
 *
 * @param items         los elementos de esta página, en el orden que pidió el llamador
 * @param page          índice de la página, empezando en cero
 * @param size          cuántos elementos entran en una página
 * @param totalElements cuántos elementos hay en total, sin paginar
 * @param totalPages    cuántas páginas da el conjunto completo
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
}
