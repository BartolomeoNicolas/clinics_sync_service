package com.bartolomeo.clinics.sync.catalog.domain.ports.in;

import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import java.util.List;

/**
 * Agenda semanal vigente de un profesional, sobre la copia local.
 *
 * <p><strong>La consume el servicio de turnos</strong> para construir la disponibilidad
 * (enunciado §7), así que su forma es parte del contrato entre los dos servicios.
 *
 * <p><strong>Devuelve también las reglas deshabilitadas.</strong> No es un descuido: es al
 * revés. El enunciado hace responsable a turnos de no ofrecer slots de agenda deshabilitada,
 * y turnos no puede cumplir una regla que nunca ve. Filtrar acá escondría el caso que allá
 * tiene que rechazar, y lo dejaría sin forma de probarse.
 *
 * <p>Si el profesional no existe el resultado es vacío, igual que en
 * {@link GetProfessionalUseCase}: no distinguir <em>"no existe"</em> de <em>"no tiene
 * agenda"</em> produciría un 404 para alguien que sí está, o un 200 vacío para alguien que
 * no. Quien traduce eso a HTTP es la fachada, que consulta el profesional primero.
 */
public interface GetWeeklySchedulesUseCase {

    /**
     * @param professionalId identificador del profesional
     * @return sus reglas semanales, ordenadas de lunes a domingo y por hora de inicio dentro
     *         de cada día. Lista vacía si no tiene ninguna. Nunca {@code null}
     */
    List<WeeklySchedule> getWeeklySchedules(long professionalId);
}
