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
 *
 * <p><strong>El orden no se pide a SQL y no es descuido.</strong> {@code dayOfWeek} está
 * guardado como texto porque así viene del contrato, y un {@code order by} sobre texto ordena
 * alfabético: Friday antes que Monday, que es lunes. El orden de la agenda —de lunes a
 * domingo— es una regla del dominio, así que lo decide el código sobre el modelo, que ya
 * tiene el {@code DayOfWeek} como enumeración. Dejarlo al azar del collation de la base
 * produciría agendas que cambian de orden entre servidores.
 *
 * @see ProfessionalCategoryRepository
 */
public interface WeeklyScheduleRepository extends JpaRepository<WeeklyScheduleEntity, Long> {

    /** Las reglas de un profesional, sin ningún orden garantizado por la base. */
    List<WeeklyScheduleEntity> findByProfessionalId(Long professionalId);
}
