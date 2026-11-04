package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.time.Instant;

/**
 * Profesional del catálogo (anexo §7 y §14.4).
 *
 * <p>{@code categoryId} es una referencia por valor al catálogo de categorías, no una asociación
 * JPA: la copia local se reconstruye completa desde el snapshot y no hay join que hacer.
 *
 * <p>Igual que en {@link ProfessionalCategory}, las bajas lógicas se conservan.
 */
public record Professional(
        long id,
        long categoryId,
        String firstName,
        String lastName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
