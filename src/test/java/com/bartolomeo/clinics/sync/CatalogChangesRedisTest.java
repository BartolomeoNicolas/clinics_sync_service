package com.bartolomeo.clinics.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogChanges;
import com.bartolomeo.clinics.sync.catalog.domain.model.SyncState;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.infrastructure.catedra.CatedraCatalogChangesAdapter;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettings;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettingsSource;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * El lector del catálogo contra un Redis real, con una ACL equivalente a la de la cátedra
 * (anexo §14.1).
 *
 * <p>El criterio del issue pide <em>"test contra Redis con ACL equivalente al de la cátedra"</em>.
 * Acá se levanta un Redis de verdad y se le da de alta un usuario con las tres reglas que lista
 * §14.1:
 *
 * <ul>
 *   <li>lectura en {@code catedra:sync:*};</li>
 *   <li>lectura y escritura en el namespace individual {@code alumnos:{groupId}:*};</li>
 *   <li>ningún acceso a {@code catedra:holds:*}, que es interno de la cátedra.</li>
 * </ul>
 *
 * <p>Se usan <em>selectores</em> de ACL de Redis 7 porque así se puede dar lectura en un
 * namespace y lectura+escritura en otro con el mismo usuario: los selectores se unen en
 * forma de O, y cada uno acota sus propios patrones de clave. Con un solo juego de permisos
 * globales no se puede lograr —o se lee todo o se escribe todo—.
 *
 * <p><strong>Qué demuestra esto y qué no.</strong> Demuestra que el adaptador lee todo lo que
 * el incremental necesita con las credenciales que entrega la integración, y que ni siquiera
 * teóricamente podría escribir en {@code catedra:sync:*} ni mirar {@code catedra:holds:*}: la
 * ACL se lo impide aunque hubiera un bug. <strong>No</strong> demuestra que la ACL real de la
 * cátedra sea esta: esa verificación necesita la VPN y quedó pendiente. Es el sustituto
 * acordado en PLAN.md §10 (Testcontainers) y hay que leerlo como evidencia local, no como
 * evidencia contra el entorno del profesor.
 */
class CatalogChangesRedisTest {

    private static final String GRUPO = "proyecto-juan-perez";

    private static final String USUARIO = "catedra-test";
    private static final String CLAVE = "clave-acl-de-prueba";

    private static GenericContainer<?> redis;
    private static RedisClient clienteDeSetUp;

    @BeforeAll
    static void levantarRedis() {
        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();

        clienteDeSetUp = RedisClient.create(
                RedisURI.create("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379)));

        darDeAltaElUsuario();

        try (StatefulRedisConnection<String, String> conexion = clienteDeSetUp.connect()) {
            sembrarElCatalogo(conexion);
        }
    }

    @AfterAll
    static void bajarRedis() {
        if (clienteDeSetUp != null) {
            clienteDeSetUp.shutdown();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    // ------------------------------------------------------------- ACL

    @Test
    @DisplayName("el usuario de la ACL puede leer las claves de versión, metadata y cambios")
    void elUsuarioPuedeLeerLoQueLaSincronizacionNecesita() {
        SyncState estado = adaptador().syncState();

        assertThat(estado.currentVersion()).isEqualTo(7L);
        assertThat(estado.oldestAvailableVersion()).isEqualTo(4L);
        assertThat(estado.publishedAt()).isEqualTo(Instant.parse("2026-09-10T13:44:59Z"));

        CatalogChanges cambios = adaptador().changes(7);
        assertThat(cambios.version()).isEqualTo(7L);
        assertThat(cambios.professionalIds()).containsExactly(15L);
        assertThat(cambios.professionalCategoryIds()).isEmpty();
        assertThat(cambios.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("el usuario de la ACL NO puede escribir en catedra:sync:*")
    void noSePuedeEscribirEnElNamespaceDeLaCatedra() {
        assertThatThrownBy(() -> escribirComoElUsuario(
                "catedra:sync:current-version", "999"))
                .isInstanceOf(RedisCommandExecutionException.class)
                .hasMessageContaining("NOPERM");
    }

    @Test
    @DisplayName("el usuario de la ACL NO puede leer catedra:holds:*")
    void noSePuedeLeerLosHoldsDeLaCatedra() {
        assertThatThrownBy(() -> leerComoElUsuario("catedra:holds:123"))
                .isInstanceOf(RedisCommandExecutionException.class)
                .hasMessageContaining("NOPERM");
    }

    @Test
    @DisplayName("el usuario de la ACL SÍ puede escribir en su namespace individual")
    void siSePuedeEscribirEnElNamespacePropio() {
        escribirComoElUsuario("alumnos:" + GRUPO + ":diagnostics:last-sync", "2026-11-02T12:00:00Z");

        assertThat(leerComoElUsuario("alumnos:" + GRUPO + ":diagnostics:last-sync"))
                .isEqualTo("2026-11-02T12:00:00Z");
    }

    // ------------------------------------------------------- lecturas

    @Test
    @DisplayName("lee las entidades por lote y descarta las que no están en el Hash")
    void leeLasEntidadesPorLote() {
        List<WeeklySchedule> horarios = adaptador().weeklySchedules(List.of(21L, 999L));

        // El 999 no existe en el Hash: un ID listado en los cambios puede corresponder a una
        // entidad que la cátedra dio de baja de verdad, y no aparecer no es un fallo.
        assertThat(horarios).hasSize(1);
        WeeklySchedule horario = horarios.get(0);
        assertThat(horario.id()).isEqualTo(21L);
        assertThat(horario.professionalId()).isEqualTo(15L);
        assertThat(horario.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(horario.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(horario.endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(horario.slotDurationMinutes()).isEqualTo(30);
        assertThat(horario.enabled()).isTrue();
        assertThat(horario.createdAt()).isEqualTo(Instant.parse("2026-07-01T12:00:00Z"));
    }

    @Test
    @DisplayName("pedir sin IDs no toca Redis y devuelve vacío")
    void pedirSinIdsDevuelveVacio() {
        assertThat(adaptador().professionals(List.of())).isEmpty();
        assertThat(adaptador().professionalCategories(List.of())).isEmpty();
    }

    @Test
    @DisplayName("una versión que no existe devuelve cambios vacíos, no un error de red")
    void unaVersionInexistenteDevuelveVacia() {
        // §14.2: si la versión salió de la ventana retenida, ya no está en Redis. Eso es la
        // señal de discontinuidad que el caso de uso traduce en un snapshot (§18.2), no un
        // fallo de conexión.
        CatalogChanges cambios = adaptador().changes(999);

        assertThat(cambios.isEmpty()).isTrue();
        assertThat(cambios.version()).isEqualTo(999L);
    }

    @Test
    @DisplayName("el estado se compara contra la ventana retenida para decidir si hay continuidad")
    void laVentanaDecideSiHayContinuidad() {
        SyncState estado = adaptador().syncState();

        // Llegar hasta la 7 con oldest=4 se puede por incremental.
        assertThat(estado.covers(7L)).isTrue();
        assertThat(estado.covers(4L)).isTrue();
        // La 3 salió de la ventana: no están todos los incrementos que faltan.
        assertThat(estado.covers(3L)).isFalse();
    }

    // --------------------------------------------------------- soporte

    private static CatedraCatalogChangesAdapter adaptador() {
        CatedraRedisSettingsSource fuente = () -> new CatedraRedisSettings(
                redis.getHost(),
                redis.getMappedPort(6379),
                USUARIO,
                CLAVE,
                "catedra:sync:*",
                "alumnos:" + GRUPO + ":*");
        return new CatedraCatalogChangesAdapter(fuente, JsonMapper.builder().build());
    }

    /**
     * Da de alta el usuario con la ACL del anexo §14.1.
     *
     * <p>Se usa {@code redis-cli} dentro del contenedor en vez del API de Lettuce: en esta
     * versión los selectores de ACL se arman con un builder de argumentos que no expone sus
     * constructores, y pasarle la regla como texto es además más legible —se lee tal cual lo
     * que la cátedra debería tener.
     *
     * <p>Dos selectores unidos en O: el primero da lectura en el namespace de la cátedra, el
     * segundo lectura y escritura en el individual. Ninguno de los dos nombra
     * {@code catedra:holds:*}, que por eso queda fuera de alcance.
     */
    private static void darDeAltaElUsuario() {
        try {
            var resultado = redis.execInContainer(
                    "redis-cli", "ACL", "SETUSER", USUARIO,
                    "on", ">" + CLAVE,
                    "(~catedra:sync:* +@read +@connection)",
                    "(~alumnos:" + GRUPO + ":* +@read +@write +@connection)");
            assertThat(resultado.getExitCode())
                    .as("ACL SETUSER devolvió %s: %s", resultado.getExitCode(), resultado.getStdout())
                    .isZero();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el usuario de la ACL.", e);
        }
    }

    /** Carga el estado publicado, tal como lo describe §14.2 a §14.5. */
    private static void sembrarElCatalogo(StatefulRedisConnection<String, String> conexion) {
        var comandos = conexion.sync();

        comandos.set("catedra:sync:current-version", "7");
        comandos.set("catedra:sync:oldest-available-version", "4");
        comandos.hset("catedra:sync:metadata", java.util.Map.of(
                "currentVersion", "7",
                "oldestAvailableVersion", "4",
                "publishedAt", "2026-09-10T13:44:59Z"));

        comandos.hset("catedra:sync:professional-categories", "1", """
                {"id": 1, "name": "Clinica medica", "description": "Atencion clinica general",
                 "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                 "updatedAt": "2026-09-10T13:40:00Z"}""");
        comandos.hset("catedra:sync:professionals", "15", """
                {"id": 15, "categoryId": 1, "firstName": "Ana", "lastName": "Gomez",
                 "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                 "updatedAt": "2026-09-10T13:40:00Z"}""");
        comandos.hset("catedra:sync:weekly-schedules", "21", """
                {"id": 21, "professionalId": 15, "dayOfWeek": "MONDAY",
                 "startTime": "09:00:00", "endTime": "12:00:00", "slotDurationMinutes": 30,
                 "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                 "updatedAt": "2026-09-10T13:40:00Z"}""");

        comandos.set("catedra:sync:changes:7", """
                {"version": 7, "changes": {"professionalCategories": [],
                 "professionals": [15], "weeklySchedules": []}}""");

        // Existe para poder comprobar que el usuario de la ACL no lo puede leer.
        comandos.set("catedra:holds:123", "interno-de-la-catedra");
    }

    /** Escribe como el usuario de la ACL, para probar qué le permite y qué no. */
    private static void escribirComoElUsuario(String clave, String valor) {
        try (RedisClient cliente = clienteDelUsuario();
                StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            conexion.sync().set(clave, valor);
        }
    }

    /** Lee como el usuario de la ACL, para probar qué le permite y qué no. */
    private static String leerComoElUsuario(String clave) {
        try (RedisClient cliente = clienteDelUsuario();
                StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            return conexion.sync().get(clave);
        }
    }

    private static RedisClient clienteDelUsuario() {
        return RedisClient.create(RedisURI.builder()
                .withHost(redis.getHost())
                .withPort(redis.getMappedPort(6379))
                .withDatabase(0)
                .withAuthentication(USUARIO, CLAVE.toCharArray())
                .build());
    }
}
