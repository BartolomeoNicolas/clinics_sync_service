package com.bartolomeo.clinics.sync.infrastructure.security;

import java.time.Instant;
import java.util.List;

import com.bartolomeo.clinics.sync.application.exception.ErrorCode;
import com.bartolomeo.clinics.sync.infrastructure.web.exception.ErrorStatusMapper;
import com.bartolomeo.clinics.sync.infrastructure.web.exception.ApiProblemDetail;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Responde {@code 401} en {@code problem+json}.
 *
 * <p>Spring, por defecto, devuelve un {@code 401} con el cuerpo vacío, o manda al navegador a
 * un formulario de login. Ninguna de las dos dos cosas sirve para un cliente que decide por
 * contrato.
 *
 * <p>Un token <em>expirado</em> se distingue del resto con {@link ErrorCode#TOKEN_EXPIRED}:
 * son dos situaciones distintas para el cliente, que en un caso renueva y en el otro tiene que
 * volver a autenticarse.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws java.io.IOException {

        ErrorCode code = isExpired(authException)
                ? ErrorCode.TOKEN_EXPIRED
                : ErrorCode.UNAUTHENTICATED;

        write(response, code, request);
    }

    /**
     * Distingue el token vencido del resto de los rechazos.
     *
     * <p>Spring no expone un campo estructurado para el motivo: el error llega como
     * {@code OAuth2Error} con código {@code invalid_token} y una descripción en texto que arma
     * Nimbus ("Jwt expired at ..."). Se compara ese texto, que es frágil y por eso queda
     * aislado en un solo método: si Nimbus cambia el wording, se toca acá y no en otro lado.
     * Un falso negativo solo hace que un token vencido responda {@code unauthenticated} en vez
     * de {@code token-expired}; los dos siguen siendo {@code 401}.
     */
    private boolean isExpired(AuthenticationException exception) {
        if (!(exception instanceof InvalidBearerTokenException invalid)) {
            return false;
        }
        String description = invalid.getError() == null
                ? null
                : invalid.getError().getDescription();
        return description != null && description.toLowerCase().contains("expired");
    }

    private void write(HttpServletResponse response, ErrorCode code,
                       HttpServletRequest request) throws java.io.IOException {

        ApiProblemDetail body = new ApiProblemDetail(
                code.getType(),
                code.getTitle(),
                ErrorStatusMapper.toHttpStatus(code).value(),
                "Falta un Bearer token válido para acceder a este recurso.",
                request.getRequestURI(),
                code.getTitle(),
                code.getCode(),
                Instant.now(),
                List.of());

        response.setStatus(ErrorStatusMapper.toHttpStatus(code).value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}