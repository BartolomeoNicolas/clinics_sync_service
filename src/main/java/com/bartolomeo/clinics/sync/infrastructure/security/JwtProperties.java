package com.bartolomeo.clinics.sync.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la validación de JWT.
 *
 * <p>Este servicio es <strong>resource server</strong>: valida tokens que emitió el servicio de
 * turnos de reservas. No emite ninguno, y por eso no tiene ni la clave privada ni el material
 * de firma (enunciado §9).
 *
 * <p>La clave pública se lee de una ruta fuera del repo. Solo la pública: la privada nunca
 * entra en este servicio ni en su máquina.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "clinics.security.jwt")
public class JwtProperties {

    /** Ruta al archivo PEM con la clave pública. Fuera del repo. */
    @NotBlank(message = "falta la ruta de la clave pública: definí CLINICS_JWT_PUBLIC_KEY_PATH")
    private String publicKeyPath;

    /** Audiencia contra la que se valida el token, además de la firma y la vigencia. */
    @NotBlank(message = "falta la audiencia del token: definí CLINICS_JWT_AUDIENCE")
    private String audience;

    /**
     * Emisor esperado, si la plataforma lo define.
     *
     * <p>Vacío significa que no se valida. Sin issuer, un token firmado por la misma clave pero
     * emitido para otra cosa se aceptaría.
     */
    private String issuer;

    /**
     * Origenes permitidos por CORS.
     *
     * <p>Lista explícita, nunca comodín: la app Android no necesita CORS pero cualquier cliente
     * web que se agregue después sí, y en ese momento tiene que estar declarado acá.
     */
    private java.util.List<String> allowedOrigins = java.util.List.of();
}