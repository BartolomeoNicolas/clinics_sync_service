package com.bartolomeo.clinics.sync.catalog.application;

/**
 * Capa de aplicación del contexto {@code catalog}: orquesta los casos de uso.
 *
 * <ul>
 *   <li>{@code usecases/}: una clase {@code *UseCaseImpl} por port {@code in} de
 *       {@code domain/ports/in}. Cada una depende solo de ports {@code out}.</li>
 *   <li>{@code service/}: la fachada. Es lo que inyectan los controllers y el único lugar
 *       donde {@code Optional} se traduce a excepción de dominio.</li>
 *   <li>{@code exception/}: excepciones de dominio, que heredan de {@code RuntimeException}.</li>
 * </ul>
 *
 * <p>Sin JPA, sin HTTP, sin Kafka.
 */
