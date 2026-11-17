package com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Una regla semanal de un profesional.
 *
 * <p><strong>Se manda también la que está apagada, y no es un descuido.</strong> El servicio
 * de turnos es quien arma los slots y quien tiene que descartar la agenda deshabilitada
 * (enunciado §7); esconderla acá le impediría cumplir una regla que nunca ve, y le dejaría sin
 * forma de probar que la descarta.
 *
 * <p>{@code dayOfWeek} viaja como texto —{@code "MONDAY"}— y no como número, igual que en el
 * contrato de la cátedra: es el formato que ya conocen los dos consumidores y el que la base
 * almacena, así que no hay una traducción más en el camino.
 *
 * @param id                 identificador de la regla
 * @param professionalId     profesional al que pertenece
 * @param dayOfWeek          día, en inglés y en mayúsculas
 * @param startTime          hora de inicio, {@code HH:mm:ss}
 * @param endTime            hora de fin, {@code HH:mm:ss}
 * @param slotDurationMinutes duración de cada turno que se deriva de esta regla
 * @param enabled            si la regla está habilitada
 * @param createdAt          cuándo se registró en la cátedra
 * @param updatedAt          cuándo lo actualizó la cátedra
 */
public record WeeklyScheduleResponse(
        long id,
        long professionalId,
        String dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMinutes,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
