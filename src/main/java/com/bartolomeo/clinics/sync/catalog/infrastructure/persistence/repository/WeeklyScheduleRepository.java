package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository;

import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.WeeklyScheduleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code weekly_schedule}.
 *
 * <p>La agenda de un profesional es lo que el servicio de turnos consume para armar la
 * disponibilidad (enunciado §7), así que aparte del reemplazo por lote hace falta leer por
 * {@code professionalId} sin traer la tabla entera.
 */
public interface WeeklyScheduleRepository extends JpaRepository<WeeklyScheduleEntity, Long> {

    /** Los horarios de un profesional, ordenados para que el llamador no tenga que hacerlo. */
    List<WeeklyScheduleEntity> findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(
            Long professionalId);
}
