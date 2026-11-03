package com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Fila de {@code catalog_version}: una versión del catálogo que quedó aplicada a la copia local.
 *
 * <p><strong>Historial y no singleton.</strong> Cada versión aplicada tiene su fila, porque la
 * detección de discontinuidad (SYNC-M1-04) necesita ver qué ventana se aplicó y no solo la
 * última: con una sola fila no habría forma de saber si se perdió una versión en el medio.
 *
 * <p>{@code source} distingue de dónde vino la aplicación —un snapshot completo o un
 * incremental— y queda acotado a {@code SNAPSHOT} / {@code Incremental} por la restricción
 * {@code ck_catalog_version_source} del changeset {@code 002-04}.
 *
 * <p>El {@code id} es autoincremental porque es solo un identificador de fila: la identidad
 * funcional es {@code version}, que además tiene restricción única —y esa única es la que hace
 * que registrar dos veces la misma versión no duplique nada.
 */
@Getter
@Setter
@Entity
@Table(name = "catalog_version")
public class CatalogVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "version", nullable = false, unique = true)
    private Long version;

    /** {@code SNAPSHOT} o {@code INCREMENTAL}. */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "snapshot_generated_at")
    private Instant snapshotGeneratedAt;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;
}
