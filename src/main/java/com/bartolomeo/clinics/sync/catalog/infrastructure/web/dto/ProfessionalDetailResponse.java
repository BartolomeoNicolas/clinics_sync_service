package com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Detalle de un profesional con su agenda completa.
 *
 * @see ProfessionalResponse
 * @see WeeklyScheduleResponse
 */
public record ProfessionalDetailResponse(
        long id,
        long categoryId,
        String firstName,
        String lastName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        List<WeeklyScheduleResponse> weeklySchedules) {
}
