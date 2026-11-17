package com.bartolomeo.clinics.sync.sync.infrastructure.redis;

import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettings;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettingsSource;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncDiagnosticsPort;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncLockPort;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * El lock de la sincronización y el registro de la última, en el namespace propio de Redis.
 *
 * <p><strong>Todo lo que se escribe va al namespace del alumno.</strong> La cátedra no se
 * toca: se lee su namespace con los permisos de lectura y se escribe en el propio con los de
 * escritura. La clave del lock es {@code alumnos:{groupId}:locks:sync} y la del diagnóstico,
 * {@code alumnos:{groupId}:diagnostics:last-sync}: las dos son del contrato de integración.
 *
 * <p><strong>El lock se toma con vencimiento y con el valor del dueño.</strong> El
 * vencimiento es lo que lo libera si el proceso muere a mitad de una sincronización; el
 * valor del dueño es lo que impide que un proceso suelte un lock que ya venció y tomó otro.
 *
 * <p><strong>El diagnóstico es un JSON en una clave.</strong> Es lo último que se escribe al
 * terminar una sincronización, y dice a qué versión se llegó y cómo terminó.
 */
@Component
@RequiredArgsConstructor
public class RedisSyncDiagnosticsAdapter implements SyncLockPort, SyncDiagnosticsPort {

    private static final Logger log = LoggerFactory.getLogger(RedisSyncDiagnosticsAdapter.class);

    private static final String LOCK_SUFIJO = ":locks:sync";
    private static final String DIAGNOSTICOS_SUFIJO = ":diagnostics:last-sync";

    private final CatedraRedisSettingsSource settingsSource;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /** Identificador de este proceso como dueño del lock. */
    private final String owner = UUID.randomUUID().toString();

    /** Cliente de Redis cacheado, con el destino al que apunta. */
    private volatile RedisClient cliente;
    private volatile String destinoCliente;

    /**
     * Si el lock ya lo tiene este hilo.
     *
     * <p>La sincronización incremental toma el lock y, si encuentra una discontinuidad, llama a
     * la completa: no es una segunda sincronización sino la misma, así que no puede quedar
     * afuera por culpa del lock que ella misma tomó.
     */
    private final ThreadLocal<Boolean> tomadoPorEsteHilo = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // ------------------------------------------------------------------ lock

    @Override
    public boolean tryAcquire(Duration ttl) {
        if (tomadoPorEsteHilo.get()) {
            // Este hilo ya tiene el lock: la sincronización incremental llama a la completa
            // cuando hay discontinuidad, y no es una segunda sincronización sino la misma.
            return true;
        }
        CatedraRedisSettings settings = settingsSource.currentRedisSettings();
        try (StatefulRedisConnection<String, String> conexion = conexion(settings)) {
            String respuesta = conexion.sync().set(
                    clave(settings, LOCK_SUFIJO),
                    owner,
                    SetArgs.Builder.nx().px(ttl.toMillis()));
            boolean adquirido = "OK".equalsIgnoreCase(respuesta);
            if (adquirido) {
                tomadoPorEsteHilo.set(Boolean.TRUE);
            }
            return adquirido;
        }
    }

    @Override
    public void release() {
        if (!Boolean.TRUE.equals(tomadoPorEsteHilo.get())) {
            return;
        }
        tomadoPorEsteHilo.remove();
        CatedraRedisSettings settings = settingsSource.currentRedisSettings();
        try (StatefulRedisConnection<String, String> conexion = conexion(settings)) {
            String clave = clave(settings, LOCK_SUFIJO);
            String actual = conexion.sync().get(clave);
            if (owner.equals(actual)) {
                conexion.sync().del(clave);
            }
        } catch (RuntimeException ex) {
            // El lock tiene vencimiento: si no se puede soltar, se suelta solo.
            log.warn("No se pudo soltar el lock de sincronización: {}", ex.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        CatedraRedisSettings settings = settingsSource.currentRedisSettings();
        try (StatefulRedisConnection<String, String> conexion = conexion(settings)) {
            return conexion.sync().exists(clave(settings, LOCK_SUFIJO)) > 0;
        } catch (RuntimeException ex) {
            // Si no se puede saber, se dice que no hay nada en curso: la sincronización
            // igual está protegida por el lock, que es la garantía que importa.
            log.warn("No se pudo consultar el lock de sincronización: {}", ex.getMessage());
            return false;
        }
    }

    // ----------------------------------------------------------- diagnóstico

    @Override
    public void recordLastSync(long version, String outcome, Instant finishedAt, String detail) {
        CatedraRedisSettings settings = settingsSource.currentRedisSettings();
        Registro registro = new Registro(version, outcome, finishedAt, detail);
        try (StatefulRedisConnection<String, String> conexion = conexion(settings)) {
            conexion.sync().set(
                    clave(settings, DIAGNOSTICOS_SUFIJO),
                    objectMapper.writeValueAsString(registro));
        } catch (RuntimeException ex) {
            // El diagnóstico es informativo: si no se puede escribir, la sincronización
            // sigue valiendo. No se deja que un log perdido eche la sincronización abajo.
            log.warn("No se pudo registrar el estado de la última sincronización: {}",
                    ex.getMessage());
        } finally {
            // La métrica propia del servicio: cuántas sincronizaciones terminaron y cómo.
            meterRegistry.counter("clinics.sync.sincronizaciones", "outcome", outcome).increment();
        }
    }

    @Override
    public Optional<LastSync> lastSync() {
        CatedraRedisSettings settings = settingsSource.currentRedisSettings();
        try (StatefulRedisConnection<String, String> conexion = conexion(settings)) {
            String cuerpo = conexion.sync().get(clave(settings, DIAGNOSTICOS_SUFIJO));
            if (cuerpo == null) {
                return Optional.empty();
            }
            Registro registro = objectMapper.readValue(cuerpo, Registro.class);
            return Optional.of(new LastSync(
                    registro.version(), registro.outcome(), registro.finishedAt(),
                    registro.detail()));
        } catch (RuntimeException ex) {
            log.warn("No se pudo leer el estado de la última sincronización: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    // ----------------------------------------------------------------- util

    private StatefulRedisConnection<String, String> conexion(CatedraRedisSettings settings) {
        return clienteDe(settings).connect();
    }

    /**
     * El cliente de Redis, cacheado por destino.
     *
     * <p>Se cachea porque abrir una conexión por cada operación es caro y, sobre todo, porque
     * cada cliente abre hilos que hay que cerrar: uno solo, reutilizado, no deja ninguno
     * suelto.
     */
    private RedisClient clienteDe(CatedraRedisSettings settings) {
        String destino = settings.host() + ":" + settings.port() + ":"
                + (settings.username() == null ? "" : settings.username());
        RedisClient actual = cliente;
        if (actual != null && destino.equals(destinoCliente)) {
            return actual;
        }
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(settings.host())
                .withPort(settings.port())
                .withDatabase(0);
        if (settings.username() != null && !settings.username().isBlank()) {
            builder.withAuthentication(
                    settings.username(),
                    settings.password() == null ? new char[0] : settings.password().toCharArray());
        }
        RedisClient nuevo = RedisClient.create(builder.build());
        cliente = nuevo;
        destinoCliente = destino;
        return nuevo;
    }

    /** La clave completa: el namespace propio del alumno más el sufijo. */
    private String clave(CatedraRedisSettings settings, String sufijo) {
        return settings.writeNamespace() + sufijo;
    }

    /** Lo que se guarda del diagnóstico. */
    record Registro(long version, String outcome, Instant finishedAt, String detail) {
    }
}
