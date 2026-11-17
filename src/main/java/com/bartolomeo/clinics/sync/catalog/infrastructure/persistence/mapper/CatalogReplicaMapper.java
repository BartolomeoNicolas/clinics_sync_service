package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.mapper;

import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.WeeklyScheduleEntity;
import java.time.DayOfWeek;

/**
 * Traducción entre el modelo de dominio y las entidades de la réplica.
 *
 * <p>Métodos estáticos a propósito: el mapper no tiene estado ni configuración, así que no
 * hay nada que inyectar ni que mockear —las dos mitades son campos uno a uno y el único punto
 * donde se toma una decisión es el día de la semana.
 *
 * <p><strong>Ida y vuelta simétricas.</strong> {@code DayOfWeek} ↔ su nombre en mayúsculas es
 * el único lugar donde un valor del dominio se vuelve texto. Se hace con
 * {@link DayOfWeek#valueOf} en los dos sentidos, así que si mañana el DDL deja de aceptar un
 * día que el dominio sí emite, la restricción {@code ck_weekly_schedule_day} revienta en vez
 * de guardar un texto inventado.
 */
public final class CatalogReplicaMapper {

    private CatalogReplicaMapper() {
    }

    // ------------------------------------------------------- dominio -> entidad

    public static ProfessionalCategoryEntity toEntity(ProfessionalCategory domain) {
        ProfessionalCategoryEntity entity = new ProfessionalCategoryEntity();
        entity.setId(domain.id());
        entity.setName(domain.name());
        entity.setDescription(domain.description());
        entity.setEnabled(domain.enabled());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        return entity;
    }

    public static ProfessionalEntity toEntity(Professional domain) {
        ProfessionalEntity entity = new ProfessionalEntity();
        entity.setId(domain.id());
        entity.setCategoryId(domain.categoryId());
        entity.setFirstName(domain.firstName());
        entity.setLastName(domain.lastName());
        entity.setEnabled(domain.enabled());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        return entity;
    }

    public static WeeklyScheduleEntity toEntity(WeeklySchedule domain) {
        WeeklyScheduleEntity entity = new WeeklyScheduleEntity();
        entity.setId(domain.id());
        entity.setProfessionalId(domain.professionalId());
        entity.setDayOfWeek(domain.dayOfWeek().name());
        entity.setStartTime(domain.startTime());
        entity.setEndTime(domain.endTime());
        entity.setSlotDurationMinutes(domain.slotDurationMinutes());
        entity.setEnabled(domain.enabled());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        return entity;
    }

    // ------------------------------------------------------- entidad -> dominio

    public static ProfessionalCategory toDomain(ProfessionalCategoryEntity entity) {
        return new ProfessionalCategory(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static Professional toDomain(ProfessionalEntity entity) {
        return new Professional(
                entity.getId(),
                entity.getCategoryId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /**
     * Devuelve el horario en dominio.
     *
     * <p>Un día fuera de la enumeración es una fila que no debería existir: la restricción
     * {@code ck_weekly_schedule_day} lo impide en la base, así que llegar acá con basura sería
     * un síntoma y no un caso de negocio. Se deja estallar en {@code valueOf} en vez de
     * devolver un valor inventado que después produciría un cálculo de disponibilidad malo en
     * silencio.
     */
    public static WeeklySchedule toDomain(WeeklyScheduleEntity entity) {
        return new WeeklySchedule(
                entity.getId(),
                entity.getProfessionalId(),
                DayOfWeek.valueOf(entity.getDayOfWeek()),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getSlotDurationMinutes(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
