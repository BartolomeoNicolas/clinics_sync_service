package com.bartolomeo.clinics.sync.infrastructure.web.exception;

import java.util.Map;

import com.bartolomeo.clinics.sync.application.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Decide con qué status HTTP responde cada {@link ErrorCode}.
 *
 * <p>Esta es la parte del contrato que <strong>sí</strong> es de infraestructura: el código
 * del error lo define el dominio ({@code application/exception/ErrorCode}) y el status con el
 * que se responde lo decidimos acá. Mantener las dos cosas juntas obligaría a que
 * {@code application} dependiera de {@code infrastructure}, que es justo la dependencia que la
 * arquitectura hexagonal prohíbe.
 *
 * <p>Es un mapa cerrado a propósito: si un error nuevo no está acá, la aplicación falla al
 * arrancar en vez de inventarse un status.
 */
public final class ErrorStatusMapper {

    private static final Map<ErrorCode, HttpStatus> STATUS = Map.ofEntries(
            Map.entry(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(ErrorCode.CATEDRA_UNAVAILABLE, HttpStatus.BAD_GATEWAY),
            Map.entry(ErrorCode.CATALOG_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ErrorCode.SYNC_DISCONTINUITY, HttpStatus.SERVICE_UNAVAILABLE));

    private ErrorStatusMapper() {
    }

    /** @return el status HTTP que corresponde al código. */
    public static HttpStatus toHttpStatus(ErrorCode code) {
        HttpStatus status = STATUS.get(code);
        if (status == null) {
            throw new IllegalStateException(
                    "ErrorCode." + code + " no tiene status HTTP mapeado. "
                    + "Agregalo en ErrorStatusMapper: el mapeo es cerrado a propósito.");
        }
        return status;
    }
}