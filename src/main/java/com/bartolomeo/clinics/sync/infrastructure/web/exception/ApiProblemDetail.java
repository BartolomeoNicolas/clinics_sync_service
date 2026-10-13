package com.bartolomeo.clinics.sync.infrastructure.web.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo de error en {@code application/problem+json}.
 *
 * <p>Responde al anexo §13 del contrato: {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code path}, {@code message} y un {@code code} estable, más
 * {@code fieldErrors} para validaciones.
 *
 * <p><strong>{@code detail} no es para el usuario final.</strong> Puede contener nombres de
 * tablas o internos. Por eso el cliente decide por {@code status} + {@code code}: son los dos
 * campos estables, y {@code message} es la versión en claro del mismo {@code code} para que
 * un humano pueda leerlo sin consultar la documentación.
 *
 * @param fieldErrors errores por campo, solo en validaciones. Se omite si está vacío.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String path,
        String message,
        String code,
        Instant timestamp,
        List<FieldError> fieldErrors) {

    /**
     * Error de un campo concreto de una entrada inválida.
     *
     * @param rejectedValue valor rechazado. Se omite cuando es sensible.
     */
    public record FieldError(
            String field,
            String message,
            String rejectedValue) {
    }

    /** Constructor para errores sin {@code fieldErrors}. */
    public ApiProblemDetail(String type, String title, int status, String detail,
                            String path, String message, String code, Instant timestamp) {
        this(type, title, status, detail, path, message, code, timestamp, List.of());
    }

    /** Deriva los errores de campo desde los errores de validación de Spring. */
    public static List<FieldError> fromValidationErrors(Map<String, List<String>> errors) {
        return errors.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(message -> new FieldError(entry.getKey(), message, null)))
                .toList();
    }
}