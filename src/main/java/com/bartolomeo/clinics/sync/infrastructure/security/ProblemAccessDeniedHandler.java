package com.bartolomeo.clinics.sync.infrastructure.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.bartolomeo.clinics.sync.application.exception.ErrorCode;
import com.bartolomeo.clinics.sync.infrastructure.web.exception.ErrorStatusMapper;
import com.bartolomeo.clinics.sync.infrastructure.web.exception.ApiProblemDetail;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Responde {@code 403} en {@code problem+json}.
 *
 * <p>Se dispara cuando el token <em>es</em> válido pero no alcanza: el usuario está
 * autenticado y no tiene permiso. Es un caso distinto del {@code 401}, y por eso lleva otro
 * código.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ErrorCode code = ErrorCode.FORBIDDEN;

        ApiProblemDetail body = new ApiProblemDetail(
                code.getType(),
                code.getTitle(),
                ErrorStatusMapper.toHttpStatus(code).value(),
                "El token es válido pero no tiene permiso sobre este recurso.",
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