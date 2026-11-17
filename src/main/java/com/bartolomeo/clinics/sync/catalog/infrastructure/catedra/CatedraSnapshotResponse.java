package com.bartolomeo.clinics.sync.catalog.infrastructure.catedra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * Body de {@code GET /api/synchronization/snapshot}, tal cual lo define el anexo §7.
 *
 * <p>Los tipos ya son de {@code java.time} y no cadenas a propósito: así el formato que exige
 * el anexo §4 se verifica en el parseo. Un instant que no venga en ISO-8601 UTC o una hora que
 * no sea {@code HH:mm}/{@code HH:mm:ss} hacen fallar acá, con el mensaje del endpoint, en vez
 * de llegar hasta el cálculo de horarios.
 *
 * <p><strong>Tolerante a campos desconocidos:</strong> la cátedra es de otro equipo y puede
 * sumar campos al snapshot; uno nuevo no puede tumbar la sincronización entera.
 *
 * <p>Los nombres de campo son los del contrato y no se reinterpretan.
 *
 * @see CatedraCatalogSnapshotAdapter
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CatedraSnapshotResponse(
        long snapshotVersion,
        Instant generatedAt,
        List<CatedraProfessionalCategoryResponse> professionalCategories,
        List<CatedraProfessionalResponse> professionals,
        List<CatedraWeeklyScheduleResponse> weeklySchedules) {
}
