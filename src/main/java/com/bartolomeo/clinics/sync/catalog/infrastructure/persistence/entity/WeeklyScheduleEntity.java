package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Fila de {@code weekly_schedule}.
 *
 * <p><strong>Los tipos copian el esquema, no el dominio.</strong> {@code day_of_week} es
 * {@code VARCHAR(9)} y acá va {@code String}: la restricción {@code ck_weekly_schedule_day}
 * es la que valida que el valor esté en la enumeración, y el mapeo a {@code DayOfWeek} lo hace
 * el mapper al pasar al dominio. Poner el enum directamente en la entidad escondería esa
 * validación en un error de mapeo en vez de dejarla donde está documentada.
 *
 * <p>Las horas son de atención y no de punto en el tiempo: van como {@link LocalTime} contra
 * una columna {@code TIME}, igual que en el contrato (anexo §4). No se les aplica huso porque
 * el de la cátedra no entrega uno.
 *
 * <p>{@code start_time < end_time}, {@code slot_duration_minutes > 0} y el día válido son
 * restricciones de la base (changeset {@code 001-04}): un horario imposible no debería poder
 * existir ni siquiera si mañana algo escapa la validación de entrada.
 *
 * @see ProfessionalEntity
 */
@Getter
@Setter
@Entity
@Table(name = "weekly_schedule")
public class WeeklyScheduleEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "professional_id", nullable = false)
    private Long professionalId;

    @Column(name = "day_of_week", nullable = false, length = 9)
    private String dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private int slotDurationMinutes;

    /** Baja lógica: la fila se conserva aunque esté apagado (anexo §7). */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
