package com.bartolomeo.clinics.sync.catalog.domain.ports.out;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogChanges;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.SyncState;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import java.util.Collection;
import java.util.List;

/**
 * Puerto de salida por el que el contexto {@code catalog} lee el estado de sincronización que
 * publica la cátedra en Redis (anexo §14.2 a §14.5).
 *
 * <p><strong>Solo lectura, y es una garantía del contrato, no una preferencia nuestra.</strong>
 * La ACL de la cátedra (§14.1) permite leer {@code catedra:sync:*} y no escribirlo, y no da
 * acceso a {@code catedra:holds:*}, que es interno de la cátedra y se manipula solo por REST.
 * Estos métodos no tienen operación de escritura alguna: no existe una forma de llamar a este
 * puerto y dejar algo escrito del lado de la cátedra.
 *
 * <p>Las lecturas de entidades son <strong>por lote y solo de los IDs que se piden</strong>.
 * El incremental (§14.5) entrega IDs y no entidades, así que el caso de uso va a buscar
 * exactamente esas y no el Hash entero: el catálogo puede ser grande y aplicar una versión de
 * tres cambios no justifica traerse miles de registros.
 *
 * <p>Las credenciales de la conexión —host, puerto, usuario y contraseña del ACL— no se pasan
 * por acá: salen de la configuración de integración que entrega la cátedra (§14.1), la misma
 * que usa el caso de uso para saber en qué namespace escribir lo propio.
 *
 * <p>Fallos: {@code catedra-unavailable} si Redis no responde o responde algo que no se pudo
 * entender. Una clave que no existe no es un fallo: es el estado normal de una versión que
 * todavía no se publicó, y se devuelve vacío.
 *
 * @see com.bartolomeo.clinics.sync.catalog.domain.model.SyncState
 */
public interface CatalogChangesPort {

    /**
     * Lee las claves de versión y el Hash de metadata (§14.2 y §14.3).
     *
     * @return el estado vigente de la ventana de versiones. Nunca {@code null}.
     * @throws com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException si
     *         Redis no está disponible o la respuesta no se pudo entender
     */
    SyncState syncState();

    /**
     * Lee los IDs que tocó una versión (§14.5).
     *
     * <p>Si la versión no existe porque ya salió de la ventana retenida, devuelve una versión
     * vacía: es la señal de discontinuidad que el caso de uso traduce en un snapshot, no un
     * error de red.
     *
     * @param version versión a leer, mayor que cero
     * @return los IDs afectados; vacío si la versión no existe o no toca nada. Nunca
     *         {@code null}.
     * @throws com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException si
     *         Redis no está disponible o la respuesta no se pudo entender
     */
    CatalogChanges changes(long version);

    /**
     * Lee categorías por ID desde {@code catedra:sync:professional-categories} (§14.4).
     *
     * <p>Los IDs que no existen simplemente no aparecen en la lista: la cátedra pudo haber
     * publicado un cambio por una entidad que después se dio de baja de verdad.
     *
     * @param ids IDs a leer; se ignoran los repetidos
     * @return las categorías encontradas, en cualquier orden. Nunca {@code null}.
     */
    List<ProfessionalCategory> professionalCategories(Collection<Long> ids);

    /**
     * Lee profesionales por ID desde {@code catedra:sync:professionals} (§14.4).
     *
     * @param ids IDs a leer; se ignoran los repetidos
     * @return los profesionales encontrados, en cualquier orden. Nunca {@code null}.
     */
    List<Professional> professionals(Collection<Long> ids);

    /**
     * Lee horarios por ID desde {@code catedra:sync:weekly-schedules} (§14.4).
     *
     * @param ids IDs a leer; se ignoran los repetidos
     * @return los horarios encontrados, en cualquier orden. Nunca {@code null}.
     */
    List<WeeklySchedule> weeklySchedules(Collection<Long> ids);
}
