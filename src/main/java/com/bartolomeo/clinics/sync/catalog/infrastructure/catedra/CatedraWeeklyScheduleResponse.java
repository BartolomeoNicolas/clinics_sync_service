package com.bartolomeo.clinics.sync.catalog.infrastructure.catedra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Horario semanal dentro del snapshot y dentro de los Hashes de Redis (anexo §7 y §14.4).
 *
 * <p>Los tres campos con formato son tipos de {@code java.time}, no cadenas:
 *
 * <ul>
 *   <li>{@link DayOfWeek} acepta exactamente los siete valores del anexo §7. Un valor fuera de
 *       esa lista falla acá, con un mensaje que nombra el campo, en vez de producir un
 *       {@code null} que reviente después.</li>
 *   <li>{@link LocalTime} acepta {@code HH:mm} y {@code HH:mm:ss}, los dos formatos que el
 *       anexo §4 obliga a aceptar, y los normaliza al mismo valor.</li>
 * </ul>
 *
 * <p>Las horas son locales de atención: no se convierten a UTC porque el contrato no entrega la
 * zona horaria que haría falta para hacerlo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CatedraWeeklyScheduleResponse(
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
