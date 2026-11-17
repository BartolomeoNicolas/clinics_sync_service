package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Horario semanal de un profesional (anexo §7 y §14.4).
 *
 * <p>Los tipos son de {@code java.time} y no cadenas porque así el contrato de la cátedra queda
 * verificado en el parseo:
 *
 * <ul>
 *   <li>{@link DayOfWeek} acepta exactamente los siete valores que enumera el anexo §7
 *       ({@code MONDAY} a {@code SUNday}); un valor fuera de esa lista falla al deserializar.</li>
 *   <li>{@link LocalTime} acepta {@code HH:mm} y {@code HH:mm:ss}, que son los dos formatos de
 *       hora que el anexo §4 obliga a aceptar. Se normalizan al mismo valor, porque
 *       {@code 09:00} y {@code 09:00:00} son la misma hora de atención.</li>
 * </ul>
 *
 * <p>Las horas son locales de atención, no UTC: no se convierten a {@link Instant} porque no
 * hay un desplazamiento que aplicar sin una zona que el contrato no entrega.
 */
public record WeeklySchedule(
        long id,
        long professionalId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMinutes,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
