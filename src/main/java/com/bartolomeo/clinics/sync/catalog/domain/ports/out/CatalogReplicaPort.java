package com.bartolomeo.clinics.sync.catalog.domain.ports.out;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import java.util.List;

/**
 * Puerto de salida por el que se reconstruye la copia local del catálogo a partir de un
 * snapshot completo (enunciado §6.1).
 *
 * <p><strong>Quién lo usa es el contexto {@code sync}, no este.</strong> Reconstruir la réplica
 * es un acto de sincronización y lo orquesta el caso de uso de allá; este puerto existe acá
 * porque los datos son de acá: el contexto dueño expone la capacidad, el que orquesta la
 * invoca, y la flecha va {@code sync → catalog} sin que ninguna de las dos partes se conozca
 * al otro lado. Ningún adaptador de este puerto habla JPA fuera de
 * {@code catalog/infrastructure/persistence}.
 *
 * <p><strong>Las tres colecciones viajan juntas o no viaja nada.</strong> El anexo §7 es
 * explícito: {@code snapshotVersion} solo se guarda como versión local cuando las tres
 * quedaron aplicadas. Por eso el puerto recibe el snapshot entero y no tres listas
 * independientes: aceptarlas por separado abriría la puerta a aplicar dos de tres y dejar la
 * copia a medias, que es exactamente el estado que el enunciado prohíbe.
 *
 * <p><strong>Invalida la copia previa.</strong> Reemplazar es borrar y volver a cargar, no
 * fusionar: un snapshot no trae el catálogo entero y su versión nueva para descartar a los
 * que ya no están, así que fusionar conservaría de baja entidades que desaparecieron. La
 * invalidación y la recarga corren dentro de la misma transacción que después guarda la
 * versión, que es lo que hace que un fallo deje la copia anterior intacta.
 *
 * <p>Las entidades deshabilitadas llegan y se guardan: son bajas lógicas, no ausencias.
 *
 * @param snapshot el snapshot completo, con sus tres colecciones nunca {@code null}
 * @throws com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException si
 *         la base no acepta los datos —referencia inexistente, violación de una restricción—
 *         y la transacción entera se revierte
 * @see com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot
 */
public interface CatalogReplicaPort {
    /**
     * Reemplaza las tres colecciones de la copia local por las del snapshot, en una sola
     * transacción.
     *
     * <p>Se presupone que corre dentro de la transacción del caso de uso: este método no
     * decide cuándo se confirma, solo garantiza que sus escrituras sean revocables.
     */
    void replaceAll(CatalogSnapshot snapshot);

    /**
     * Aplica sobre la copia local las entidades que cambiaron en una versión.
     *
     * <p><strong>Es un upsert, no un reemplazo.</strong> El incremento solo trae lo que cambió,
     * así que las entidades que no aparecen quedan como estaban: borrarlas sería perder el
     * catálogo entero en cada versión.
     *
     * <p><strong>Las bajas lógicas se aplican como lo que son.</strong> Una entidad que llega
     * deshabilitada se guarda deshabilitada, no se borra: el contrato las maneja así, y borrar
     * perdería el historial y rompería las referencias de las que apuntan a ella.
     *
     * @param categories  categorías que cambiaron, con su estado vigente
     * @param professionals profesionales que cambiaron, con su estado vigente
     * @param schedules  horarios semanales que cambiaron, con su estado vigente
     */
    void upsert(
            List<ProfessionalCategory> categories,
            List<Professional> professionals,
            List<WeeklySchedule> schedules);
}
