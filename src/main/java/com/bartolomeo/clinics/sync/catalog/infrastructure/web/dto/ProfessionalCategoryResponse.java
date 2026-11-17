package com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto;

import java.time.Instant;

/**
 * Una categoría de profesional.
 *
 * <p>Se mandan también las deshabilitadas: quien arma el filtro tiene que poder mostrarlas o
 * saber que existen para excluirlas, y esa decisión no le corresponde al catálogo. Si una
 * categoría deja de servir para filtrar, eso se ve en el listado de profesionales, no acá.
 *
 * @param id          identificador
 * @param name        nombre, que es lo que se muestra en el filtro
 * @param description descripción, opcional
 * @param enabled     si la categoría está habilitada
 * @param createdAt   cuándo se registró en la cátedra
 * @param updatedAt   cuándo lo actualizó la cátedra
 */
public record ProfessionalCategoryResponse(
        long id,
        String name,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
