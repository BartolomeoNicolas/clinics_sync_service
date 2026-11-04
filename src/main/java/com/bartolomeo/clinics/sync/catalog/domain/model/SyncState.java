package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.time.Instant;

/**
 * Estado de la ventana de versiones que publica la cátedra en Redis (anexo §14.2 y §14.3).
 *
 * <p>Las dos claves de versión dicen qué hay disponible; {@code publishedAt} dice cuándo se
 * publicó la última. Se leen de sitios distintos —las versiones son claves sueltas y la
 * metadata es un Hash— porque así los define el contrato, y los tres valores viajan juntos
 * porque juntos responden la pregunta del caso de uso: <em>¿puedo aplicar el incremental o
 * necesito un snapshot?</em>
 *
 * <p><strong>La regla que gobierna este valor:</strong> si la versión local es menor que
 * {@code oldestAvailableVersion}, la cátedra ya descartó incrementos que necesitamos y no hay
 * forma de ponernos al día por incremental; hay que pedir un snapshot REST. Esa comparación es
 * del caso de uso, no de este registro.
 *
 * @param currentVersion      última versión publicada, {@code catedra:sync:current-version}
 * @param oldestAvailableVersion la más vieja que todavía se puede aplicar,
 *                            {@code catedra:sync:oldest-available-version}
 * @param publishedAt         {@code publishedAt} del Hash {@code catedra:sync:metadata}
 */
public record SyncState(
        long currentVersion,
        long oldestAvailableVersion,
        Instant publishedAt) {

    /**
     * Si la cátedra todavía retiene los incrementos que faltan para llegar a {@code target}.
     *
     * <p>Falso significa discontinuidad: la ventana avanzó y se nos pasaron de largo, así que
     * toca snapshot (anexo §18.2).
     */
    public boolean covers(long target) {
        return target >= oldestAvailableVersion;
    }
}
