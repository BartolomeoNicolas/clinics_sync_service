package com.bartolomeo.clinics.sync.sync.application.usecases;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogChanges;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.SyncState;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogChangesPort;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogReplicaPort;
import com.bartolomeo.clinics.sync.sync.domain.model.SyncError;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncFullSnapshotUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncIncrementalUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.CatalogVersionPort;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncErrorPort;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link SyncIncrementalUseCase}.
 *
 * <p><strong>Los cambios se aplican en orden, de a una versión.</strong> Se empieza en la
 * versión local más uno y se termina en la que hay disponible. El orden no es un detalle: cada
 * versión trae el estado de las entidades que cambió, y aplicar la 7 antes que la 5 dejaría
 * la copia con una mezcla que no existió nunca.
 *
 * <p><strong>La versión local avanza en la misma transacción que los cambios.</strong> Es lo
 * que pide el enunciado §6.2: la versión local solo puede avanzar cuando la unidad de trabajo
 * terminó. Si el registro de la versión se confirmara aparte, un fallo a mitad de una versión
 * dejaría la versión local adelantada y el catálogo atrás, y de ese estado no se sale sin otro
 * snapshot. Aquí toda la vuelta termina o no termina: la transacción es la unidad de trabajo.
 *
 * <p><strong>Una discontinuidad se reconstruye, no se intenta.</strong> El anexo §14.2 lo dice
 * sin rodeos: si la versión local es menor que la más vieja disponible, no están todos los
 * incrementos necesarios y hay que pedir un snapshot REST. Es el caso del anexo §18.2: con la
 * versión local en 3 y la ventana empezando en 4, no se intenta leer {@code changes:4}, porque
 * falta la versión que conecta el estado local. Reconstruir es más caro, y aun así es lo
 * único que deja la copia consistente.
 *
 * <p><strong>Lo que se relee es el estado vigente.</strong> Los cambios de una versión dicen
 * <em>qué</em> cambió, no <em>cómo quedó</em>: el estado de cada identificador se relee del
 * hash de Redis. Así, un cambio que se aplicó dos veces, o que llegó tarde, deja la copia en
 * el estado actual y no en un estado intermedio que ya no existe.
 *
 * <p><strong>Repetir la misma versión no cambia el resultado.</strong> El upsert escribe el
 * estado vigente de cada entidad, así que aplicar dos veces la misma versión deja lo mismo.
 */
@Component
public class SyncIncrementalUseCaseImpl implements SyncIncrementalUseCase {

    /** De qué falló este caso de uso. Valor estable: lo consultan el endpoint de errores. */
    private static final String KIND = "incremental";

    private static final Logger log = LoggerFactory.getLogger(SyncIncrementalUseCaseImpl.class);

    private final CatalogChangesPort changesPort;
    private final CatalogReplicaPort replicaPort;
    private final CatalogVersionPort versionPort;
    private final SyncFullSnapshotUseCase snapshotUseCase;
    private final SyncErrorPort errorPort;

    public SyncIncrementalUseCaseImpl(
            CatalogChangesPort changesPort,
            CatalogReplicaPort replicaPort,
            CatalogVersionPort versionPort,
            SyncFullSnapshotUseCase snapshotUseCase,
            SyncErrorPort errorPort) {
        this.changesPort = changesPort;
        this.replicaPort = replicaPort;
        this.versionPort = versionPort;
        this.snapshotUseCase = snapshotUseCase;
        this.errorPort = errorPort;
    }

    @Override
    @Transactional
    public long applyIncremental() {

        long local = versionPort.currentVersion();

        try {
            SyncState state = changesPort.syncState();

            if (local < state.oldestAvailableVersion()) {
                // Falta la versión que conecta el estado local con la ventana disponible:
                // el incremento no se puede encadenar y hay que reconstruir (anexo §14.2).
                log.warn(
                        "Discontinuidad: la versión local es {} y la ventana empieza en {}. "
                                + "Se reconstruye con un snapshot completo.",
                        local, state.oldestAvailableVersion());
                return snapshotUseCase.applyFullSnapshot();
            }

            long aplicada = local;
            for (long version = local + 1; version <= state.currentVersion(); version++) {
                aplicada = aplicarVersion(version, state);
            }

            if (aplicada != local) {
                log.info("Sincronización incremental: la versión local pasó de {} a {}",
                        local, aplicada);
            }
            return aplicada;

        } catch (RuntimeException failure) {
            registrarError(local, failure);
            throw failure;
        }
    }

    /**
     * Aplica una versión: lee qué cambió, relee el estado vigente y lo escribe.
     *
     * <p>Una versión que no toca nada también se registra: la versión local tiene que avanzar
     * igual, o el siguiente incremento empezaría de nuevo desde la misma versión.
     */
    private long aplicarVersion(long version, SyncState state) {

        CatalogChanges changes = changesPort.changes(version);

        if (!changes.isEmpty()) {
            List<ProfessionalCategory> categorias =
                    changesPort.professionalCategories(changes.professionalCategoryIds());
            List<Professional> profesionales =
                    changesPort.professionals(changes.professionalIds());
            List<WeeklySchedule> horarios =
                    changesPort.weeklySchedules(changes.weeklyScheduleIds());

            replicaPort.upsert(categorias, profesionales, horarios);
        }

        versionPort.recordIncrementalApplied(version, state.publishedAt());
        return version;
    }

    /**
     * Deja constancia del fallo sin dejar que la del registro tape al original.
     *
     * <p>Corre todavía dentro de la transacción que va a revertirse, y por eso el adaptador usa
     * {@code REQUIRES_NEW}: el registro se confirma por su lado y no se lo lleva el rollback.
     */
    private void registrarError(Long version, RuntimeException failure) {
        try {
            errorPort.record(SyncError.now(KIND, version, mensajeDe(failure), trazaDe(failure)));
        } catch (RuntimeException sinRegistro) {
            log.warn(
                    "No se pudo registrar el error de sincronizacion en sync_error; "
                            + "se conserva el fallo original.",
                    sinRegistro);
        }
    }

    /** Mensaje para la columna {@code message}, que es {@code NOT NULL}. */
    private static String mensajeDe(RuntimeException failure) {
        String mensaje = failure.getMessage();
        return mensaje == null || mensaje.isBlank() ? failure.getClass().getName() : mensaje;
    }

    /** Traza completa para la columna {@code detail}, que es texto libre. */
    private static String trazaDe(RuntimeException failure) {
        StringWriter buffer = new StringWriter();
        failure.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }
}
