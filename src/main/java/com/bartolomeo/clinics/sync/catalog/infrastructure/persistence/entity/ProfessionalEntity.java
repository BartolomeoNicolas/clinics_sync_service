package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Fila de {@code professional}.
 *
 * <p>{@code category_id} es una columna suelta con restricción de clave ajena, no una
 * asociación {@code @ManyToOne}: la referencia es un valor que viene del snapshot y no hay
 * nada que resolver por join —la búsqueda por categoría se hace con un filtro, no navegando
 * el grafo de entidades.
 *
 * <p>{@code first_name} y {@code last_name} son no nulos porque el anexo §7 los trae siempre:
 * un profesional sin nombre no es un dato válido, es una fila rota.
 *
 * @see ProfessionalCategoryEntity
 */
@Getter
@Setter
@Entity
@Table(name = "professional")
public class ProfessionalEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    /** Baja lógica: la fila se conserva aunque esté apagado (anexo §7). */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
