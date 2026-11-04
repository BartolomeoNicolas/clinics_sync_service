package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Fila de {@code professional_category}.
 *
 * <p><strong>El esquema es de Liquibase, nunca de Hibernate.</strong> Por eso cada columna
 * nombra a su columna explícitamente en vez de dejar que la estrategia de nombres la deduzca:
 * el DDL vive en {@code db/changelog/001-catalog-tables.xml} y este mapa tiene que decir qué
 * campo corresponde a qué, no inferirlo. {@code ddl-auto} está en {@code none}.
 *
 * <p>Los nombres y los largos copian el snapshot de la cátedra (anexo §7): esta tabla es una
 * réplica, no un modelo inventado.
 *
 * <p>No hay asociación JPA hacia las otras entidades. La referencia al catálogo es un valor
 * ({@code category_id}) igual que en el dominio, porque la copia se reconstruye completa
 * desde el snapshot y no hay nada que recorrer por join.
 */
@Getter
@Setter
@Entity
@Table(name = "professional_category")
public class ProfessionalCategoryEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Baja lógica: la fila se conserva aunque esté apagada (anexo §7). */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
