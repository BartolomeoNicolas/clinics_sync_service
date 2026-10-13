package com.bartolomeo.clinics.sync;

import java.util.List;

import com.bartolomeo.clinics.sync.application.exception.ApplicationException;
import com.bartolomeo.clinics.sync.application.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de prueba para los límites HTTP.
 *
 * <p>Vive en {@code src/test}: hasta que M1 no traiga endpoints reales, no hay nada en producción
 * que exercitar. Inventar un controller de producción solo para probar seguridad sería meter
 * código falso en la entrega.
 *
 * <p>Cubre los tres caminos que tienen que traducirse a {@code problem+json}: uno que exige
 * token, uno que valida el cuerpo, y uno que lanza una excepción de negocio.
 */
@RestController
@RequestMapping("/probe")
class ProbeController {

    /** Exige autenticación: sin token tiene que dar 401. */
    @GetMapping("/protected")
    ResponseEntity<String> protectedEndpoint() {
        return ResponseEntity.ok("accessible");
    }

    /** Valida el cuerpo: debe devolver 400 con `fieldErrors`. */
    @PostMapping("/validated")
    ResponseEntity<String> validatedEndpoint(@Valid @RequestBody ProbeRequest request) {
        return ResponseEntity.ok(request.name());
    }

    /** Lanza una excepción de negocio: debe devolver su `code` estable, no un 500 genérico. */
    @GetMapping("/business-error")
    ResponseEntity<String> businessErrorEndpoint() {
        throw new ProbeException("el catálogo local todavía no está disponible");
    }

    record ProbeRequest(@NotBlank @Size(max = 10) String name) {
    }

    /** Excepción de negocio de mentira, con un código real. */
    static class ProbeException extends ApplicationException {
        ProbeException(String message) {
            super(ErrorCode.CATALOG_UNAVAILABLE, message);
        }
    }
}