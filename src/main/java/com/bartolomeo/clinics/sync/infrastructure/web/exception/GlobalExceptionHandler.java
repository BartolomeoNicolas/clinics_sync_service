package com.bartolomeo.clinics.sync.infrastructure.web.exception;

import java.time.Instant;
import java.util.List;

import com.bartolomeo.clinics.sync.application.exception.ApplicationException;
import com.bartolomeo.clinics.sync.application.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce excepciones a {@code application/problem+json}.
 *
 * <p>Es el <strong>único</strong> lugar del servicio donde se decide qué devuelve un error. Un
 * controller no traduce excepciones: el repo del profesor lo hace con {@code try/catch} y
 * {@code ResponseStatusException}, y acá es una desviación deliberada (D-2 del plan maestro)
 * porque el contrato con la cátedra manda que el cliente decida por {@code status} +
 * {@code code} y no por texto libre.
 *
 * <p>Lo que no llega acá nunca debe filtrarse: los {@code 500} genéricos no incluyen la
 * excepción en la respuesta, solo en el log. El enunciado (§9) pide no exponer secretos ni
 * detalles internos en las respuestas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Errores de negocio, cada uno con su código fijo.
     *
     * <p>El mensaje va a {@code detail} y el código a {@code code}, nunca al revés: el cliente
     * tiene que poder cambiar el texto sin romper a nadie.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiProblemDetail> handleApplication(
            ApplicationException ex, HttpServletRequest request) {

        ErrorCode code = ex.getErrorCode();
        log.debug("Error de aplicación {} en {}: {}",
                code.getCode(), request.getRequestURI(), ex.getMessage());

        return respond(code, ex.getMessage(), request, List.of());
    }

    /** Errores de validación del cuerpo: van campo por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiProblemDetail.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(error -> new ApiProblemDetail.FieldError(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue() == null ? null : error.getRejectedValue().toString()))
                .toList();

        return respond(ErrorCode.VALIDATION_ERROR, "La solicitud tiene campos inválidos.",
                request, fieldErrors);
    }

    /** Validación de los parámetros del método, no del cuerpo. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiProblemDetail> handleParameterValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {

        return respond(ErrorCode.VALIDATION_ERROR, "Parámetros inválidos.", request, List.of());
    }

    /** JSON ilegible o con tipos que no cierran. No es un 500: es del cliente. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiProblemDetail> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        return respond(ErrorCode.VALIDATION_ERROR, "El cuerpo no se pudo leer.",
                request, List.of());
    }

    /** Ruta inexistente. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiProblemDetail> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {

        return respond(ErrorCode.NOT_FOUND, "No existe el recurso solicitado.",
                request, List.of());
    }

    /**
     * Cualquier otra cosa.
     *
     * <p>La respuesta dice {@code internal-error} y nada más. El detalle real va al log: si
     * un nombre de clase o de configuración se filtra al cliente, eso ya es una fuga.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblemDetail> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        log.error("Error no previsto en {}", request.getRequestURI(), ex);

        return respond(ErrorCode.INTERNAL_ERROR, "Ocurrió un error no previsto.",
                request, List.of());
    }

    private ResponseEntity<ApiProblemDetail> respond(
            ErrorCode code,
            String detail,
            HttpServletRequest request,
            List<ApiProblemDetail.FieldError> fieldErrors) {

        HttpStatus status = ErrorStatusMapper.toHttpStatus(code);

        ApiProblemDetail body = new ApiProblemDetail(
                code.getType(),
                code.getTitle(),
                status.value(),
                detail,
                request.getRequestURI(),
                code.getTitle(),
                code.getCode(),
                Instant.now(),
                fieldErrors);

        // El content type va explícito: ApiProblemDetail es un record propio, no el
        // ProblemDetail de Spring, así que Spring no lo deduce y mandaría application/json.
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}