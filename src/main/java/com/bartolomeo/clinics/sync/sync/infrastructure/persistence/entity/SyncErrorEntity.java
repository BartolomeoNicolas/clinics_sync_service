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
 * Fila de {@code sync_error}: un intento de sincronización que terminó mal.
 *
 * <p><strong>Es una bitácora, no una respuesta de API.</strong> {@code detail} es texto libre a
 * propósito y puede llevar la traza completa: nadie la ve desde el cliente final, y quien
 * investiga un fallo necesita el detalle, no un resumen. {@code message}, en cambio, sí está
 * acotado al largo de la columna, así que el adaptador lo recorta antes de guardar.
 *
 * <p>Se consulta desde el propio servicio (SYNC-M1-07) sin tocar la base por otros medios, que
 * es lo que pide el enunciado §4.1.
 */
@Getter
@Setter
@Entity
@Table(name = "sync_error")
public class SyncErrorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "kind", nullable = false, length = 64)
    private String kind;

    /** Puede ser {@code null} si el fallo fue antes de saber con qué versión se trabajaba. */
    @Column(name = "attempted_version")
    private Long attemptedVersion;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "detail")
    private String detail;
}
