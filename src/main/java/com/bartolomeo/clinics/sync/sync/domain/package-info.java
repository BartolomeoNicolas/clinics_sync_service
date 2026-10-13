package com.bartolomeo.clinics.sync.sync.domain;

/**
 * Dominio del contexto {@code sync}: versión local del catálogo y orquestación.
 *
 * <p><strong>Sin Spring, sin JPA.</strong> Solo modelo y puertos.
 *
 * <p>Acá vive la regla de versión aplicada y la lógica que decide entre sincronización
 * completa e incremental: son reglas de negocio, no de transporte.
 */
