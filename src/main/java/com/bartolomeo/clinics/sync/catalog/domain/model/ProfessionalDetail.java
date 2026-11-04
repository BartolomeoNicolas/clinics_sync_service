package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.util.List;

/**
 * Lo que devuelve el detalle de un profesional: la ficha y su agenda.
 *
 * <p>Existe como una sola pieza porque pedirlas por separado obligaría a comprobar dos veces
 * que el profesional existe: la primera consulta contesta 404 y la segunda volvería a
 * preguntar lo mismo para no arriesgar devolver la agenda de nadie. Juntas, se pregunta una
 * vez y se responde una sola cosa.
 *
 * @param professional    la ficha. Nunca {@code null}: llegar acá sin profesional sería un
 *                        error, no un caso vacío
 * @param weeklySchedules su agenda semanal, incluidas las reglas deshabilitadas. Puede venir
 *                        vacía si el profesional todavía no tiene ninguna
 */
public record ProfessionalDetail(Professional professional, List<WeeklySchedule> weeklySchedules) {
}
