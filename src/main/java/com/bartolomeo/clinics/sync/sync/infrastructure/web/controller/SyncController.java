package com.bartolomeo.clinics.sync.sync.infrastructure.web.controller;

import com.bartolomeo.clinics.sync.sync.domain.model.SyncError;
import com.bartolomeo.clinics.sync.sync.domain.model.SyncStatus;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.GetSyncStatusUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.ListSyncErrorsUseCase;
import com.bartolomeo.clinics.sync.sync.infrastructure.web.dto.SyncErrorResponse;
import com.bartolomeo.clinics.sync.sync.infrastructure.web.dto.SyncStatusResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El estado y los errores de la sincronización (enunciado §4.1: informar estado y errores).
 *
 * <p><strong>Son endpoints de operación, no de negocio.</strong> Dicen cómo está el servicio,
 * no qué hay en el catálogo: el catálogo se consulta por el endpoint de búsqueda, que devuelve
 * los profesionales con sus filtros. Acá lo que se contesta es si la copia está al día y qué
 * le pasó a la última sincronización.
 *
 * <p><strong>Quien los consulta no abre la base.</strong> El estado sale de las mismas fuentes
 * que usa la sincronización —el historial de versiones y el estado que publica la cátedra— y
 * los errores de la tabla de control del servicio. No hace falta credencial de base ni
 * conocer el esquema para ver si el servicio está sano.
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final GetSyncStatusUseCase statusUseCase;
    private final ListSyncErrorsUseCase errorsUseCase;

    /**
     * El estado: versiones, si hay una sincronización en curso y cómo terminó la última.
     */
    @GetMapping("/status")
    public ResponseEntity<SyncStatusResponse> status() {
        SyncStatus status = statusUseCase.status();

        return ResponseEntity.ok(new SyncStatusResponse(
                status.localVersion(),
                status.remoteVersion(),
                status.oldestAvailableVersion(),
                status.running(),
                status.lastSyncAt(),
                status.lastSyncOutcome(),
                status.lastSyncVersion()));
    }

    /**
     * Los errores de la sincronización, del más nuevo al más viejo.
     */
    @GetMapping("/errors")
    public ResponseEntity<List<SyncErrorResponse>> errors() {
        List<SyncErrorResponse> errores = errorsUseCase.recentErrors().stream()
                .map(SyncController::aResponse)
                .toList();

        return ResponseEntity.ok(errores);
    }

    private static SyncErrorResponse aResponse(SyncError error) {
        return new SyncErrorResponse(
                error.occurredAt(),
                error.kind(),
                error.attemptedVersion(),
                error.message());
    }
}
