package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot completo del catálogo que devuelve {@code GET /api/synchronization/snapshot}
 * (anexo §7).
 *
 * <p><strong>Las tres colecciones viajan juntas y no se pueden aplicar a medias.</strong> El
 * anexo §7 es explícito: <em>"snapshotVersion solo se guarda como version local cuando las tres
 * colecciones quedaron aplicadas correctamente"</em>. Por eso el snapshot es un valor único y no
 * tres llamadas independientes: quien lo recibe tiene todo lo necesario para decidir si avanza
 * la versión local o se queda como estaba.
 *
 * <p>{@code snapshotVersion} es la versión del catálogo al momento de generarse, no un contador
 * nuestro. Se compara con la versión local para saber si hay algo nuevo.
 *
 * <p>Las listas pueden venir vacías (una cátedra sin horarios cargados) pero no {@code null}:
 * el adaptador las normaliza, para que el caso de uso no tenga que distinguir entre
 * "colección ausente" y "colección vacía".
 *
 * @param professionalCategories incluye las deshabilitadas, ver {@link ProfessionalCategory}
 * @param professionals          incluye las deshabilitadas, ver {@link Professional}
 * @param weeklySchedules        incluye las deshabilitadas, ver {@link WeeklySchedule}
 */
public record CatalogSnapshot(
        long snapshotVersion,
        Instant generatedAt,
        List<ProfessionalCategory> professionalCategories,
        List<Professional> professionals,
        List<WeeklySchedule> weeklySchedules) {

    /**
     * Normaliza las listas a vacío en lugar de {@code null}.
     *
     * <p>Un campo ausente en el JSON de la cátedra es un caso que el contrato no contempla pero
     * que un servicio de otro equipo podría producir. Prefiero tratarlo como colección vacía,
     * que además es lo que el caso de uso haría igual: no hay nada que aplicar.
     */
    public CatalogSnapshot {
        professionalCategories =
                professionalCategories == null ? List.of() : List.copyOf(professionalCategories);
        professionals = professionals == null ? List.of() : List.copyOf(professionals);
        weeklySchedules = weeklySchedules == null ? List.of() : List.copyOf(weeklySchedules);
    }
}
