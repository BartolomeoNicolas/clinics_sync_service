package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.time.Instant;

/**
 * Categoría profesional del catálogo (anexo §7 y §14.4).
 *
 * <p>Espejo del objeto que devuelve la cátedra, con los tipos de Java en lugar de cadenas:
 * los instantes van como {@link Instant} porque el contrato los define en ISO-8601 UTC
 * (anexo §4), y así un valor mal formado falla al parsear y no se cuela hasta el cálculo de
 * horarios.
 *
 * <p>{@code enabled} es una baja lógica: la cátedra conserva las categorías deshabilitadas en
 * el Hash y en el snapshot, y este modelo también. Filtrarlas acá haría que una baja pareciera
 * una desaparición y rompería el incremental, que referencia por ID entidades que pueden estar
 * apagadas.
 */
public record ProfessionalCategory(
        long id,
        String name,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
