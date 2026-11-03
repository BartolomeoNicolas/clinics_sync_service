package com.bartolomeo.clinics.sync.sync.application.usecases;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogReplicaPort;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogSnapshotPort;
import com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException;
import com.bartolomeo.clinics.sync.sync.domain.model.SyncError;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncFullSnapshotUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.CatalogVersionPort;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncErrorPort;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link SyncFullSnapshotUseCase}.
 *
 * <p><strong>La transacción entera es el caso de uso.</strong> {@code @Transactional} en este
 * método hace que pedir el snapshot, reemplazar las tres colecciones y registrar la versión
 * sean una sola unidad: o las tres cosas quedan hechas, o no queda ninguna. El anexo §7 pide
 * exactamente eso al decir que {@code snapshotVersion} solo se guarda cuando las tres
 * colecciones quedaron aplicadas, y el enunciado §6.1 lo repite al pedir registrar la versión
 * que <em>corresponde</em> a los datos almacenados. Sin esta garantía, un fallo en la tercera
 * operación dejaría la versión adelantada con la copia incompleta, y de ese estado no se sale
 * sin detectarlo antes.
 *
 * <p><strong>El error de registro no puede tapar al original.</strong> {@link SyncErrorPort}
 * corre en su propia transacción para que el registro sobreviva al rollback, pero si tampoco
 * se pudiera escribir —porque la base está caída, que suele ser la causa—, lo único correcto
 * es seguir reportando el fallo que motivó todo. Por eso el registro va en un {@code try} que
 * solo avisa por log.
 *
 * <p>No se envuelve todo en un {@code catch} genérico que traduzca a una excepción propia:
 * quien llama ya sabe leer {@code ApplicationException} y un fallo de base es un fallo de base.
 * Lo que sí se hace es dejar rastro, que es lo que pide el enunciado §11 punto 15.
 */
@Component
public class SyncFullSnapshotUseCaseImpl implements SyncFullSnapshotUseCase {

    /** De qué falló este caso de uso. Valor estable: lo consultan el endpoint de errores. */
    private static final String KIND = "snapshot";

    private static final Logger log = LoggerFactory.getLogger(SyncFullSnapshotUseCaseImpl.class);

    private final CatalogSnapshotPort snapshotPort;
    private final CatalogReplicaPort replicaPort;
    private final CatalogVersionPort versionPort;
    private final SyncErrorPort errorPort;

    public SyncFullSnapshotUseCaseImpl(
            CatalogSnapshotPort snapshotPort,
            CatalogReplicaPort replicaPort,
            CatalogVersionPort versionPort,
            SyncErrorPort errorPort) {
        this.snapshotPort = snapshotPort;
        this.replicaPort = replicaPort;
        this.versionPort = versionPort;
        this.errorPort = errorPort;
    }

    @Override
    @Transactional
    public long applyFullSnapshot() {

        // Se arranca en null a propósito: si el fallo es pedir el snapshot todavía no se sabe
        // contra qué versión se trabajaba, y registrar un número inventado despistaría más de
        // lo que ayuda.
        Long version = null;
        try {
            CatalogSnapshot snapshot = snapshotPort.fetchSnapshot();
            version = snapshot.snapshotVersion();

            replicaPort.replaceAll(snapshot);
            versionPort.recordSnapshotApplied(version, snapshot.generatedAt());

            return version;

        } catch (RuntimeException failure) {
            registrarError(version, failure);
            throw failure;
        }
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

    /**
     * Mensaje para la columna {@code message}.
     *
     * <p>{@code getMessage()} puede devolver {@code null} —hay excepciones que no traen—, y la
     * columna es {@code NOT NULL}. Ante un mensaje vacío se usa el nombre de la clase: al menos
     * dice qué tipo de fallo fue.
     */
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
