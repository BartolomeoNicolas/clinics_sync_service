package com.bartolomeo.clinics.sync.catalog.domain;

/**
 * Dominio del contexto {@code catalog}.
 *
 * <p><strong>Sin Spring, sin JPA.</strong> Solo el modelo (POJOs en {@code model}) y los puertos
 * ({@code ports/in} y {@code ports/out}).
 *
 * <p>Los ports {@code in} —las interfaces de caso de uso— viven acá y no en
 * {@code application}: es la convención del repo de referencia del profesor.
 *
 * <p>Regla verificable: ningún archivo de este paquete puede importar
 * {@code org.springframework.*} ni {@code jakarta.persistence.*}.
 */
