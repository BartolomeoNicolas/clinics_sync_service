package com.bartolomeo.clinics.sync.catalog.infrastructure.web.mapper;

import com.bartolomeo.clinics.sync.catalog.domain.model.PageResult;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalDetail;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.ProfessionalCategoryResponse;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.ProfessionalDetailResponse;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.ProfessionalResponse;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.ProfessionalSearchResponse;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.WeeklyScheduleResponse;
import java.util.List;

/**
 * Traducción entre el modelo de dominio y las respuestas HTTP del catálogo.
 *
 * <p><strong>De dominio a DTO y nunca al revés.</strong> El dominio no conoce a Jackson ni a
 * Spring, y los DTO no son el modelo: si mañana una respuesta deja de mandar un campo, se
 * cambia acá y no se toca el dominio. Es el mismo argumento por el que no se serializa un
 * registro de dominio directamente.
 *
 * <p>Métodos estáticos a propósito: el mapper no tiene estado, ni dependencias, ni nada que
 * configurar, así que no hay nada que inyectar ni que mockear. Los mappers con dependencias
 * —los que hablan con una tecnología— sí se inyectan; éste solo mueve campos.
 *
 * <p>Null-safe en serio y no por costumbre: una descripción puede venir en {@code null} de la
 * cátedra y un detalle pedido sobre una copia recién reconstruida también, y ninguna de esas
 * dos cosas es motivo para que la respuesta entera reviente con un 500.
 */
public final class CatalogWebMapper {

    private CatalogWebMapper() {
    }

    public static ProfessionalResponse toResponse(Professional domain) {
        if (domain == null) {
            return null;
        }
        return new ProfessionalResponse(
                domain.id(),
                domain.categoryId(),
                domain.firstName(),
                domain.lastName(),
                domain.enabled(),
                domain.createdAt(),
                domain.updatedAt());
    }

    public static WeeklyScheduleResponse toResponse(WeeklySchedule domain) {
        if (domain == null) {
            return null;
        }
        return new WeeklyScheduleResponse(
                domain.id(),
                domain.professionalId(),
                domain.dayOfWeek().name(),
                domain.startTime(),
                domain.endTime(),
                domain.slotDurationMinutes(),
                domain.enabled(),
                domain.createdAt(),
                domain.updatedAt());
    }

    public static ProfessionalCategoryResponse toResponse(ProfessionalCategory domain) {
        if (domain == null) {
            return null;
        }
        return new ProfessionalCategoryResponse(
                domain.id(),
                domain.name(),
                domain.description(),
                domain.enabled(),
                domain.createdAt(),
                domain.updatedAt());
    }

    public static ProfessionalDetailResponse toDetailResponse(ProfessionalDetail detail) {
        if (detail == null) {
            return null;
        }
        List<WeeklyScheduleResponse> schedules = detail.weeklySchedules() == null
                ? List.of()
                : detail.weeklySchedules().stream().map(CatalogWebMapper::toResponse).toList();

        ProfessionalResponse professional = toResponse(detail.professional());
        return new ProfessionalDetailResponse(
                professional.id(),
                professional.categoryId(),
                professional.firstName(),
                professional.lastName(),
                professional.enabled(),
                professional.createdAt(),
                professional.updatedAt(),
                schedules);
    }

    public static ProfessionalSearchResponse toSearchResponse(PageResult<Professional> page) {
        if (page == null) {
            return null;
        }
        return new ProfessionalSearchResponse(
                page.items().stream().map(CatalogWebMapper::toResponse).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
