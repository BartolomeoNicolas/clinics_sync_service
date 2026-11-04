package com.bartolomeo.clinics.sync.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalCategoryRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.WeeklyScheduleRepository;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettings;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettingsSource;
import com.bartolomeo.clinics.sync.support.TestKeys;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncFullSnapshotUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncIncrementalUseCase;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.CatalogVersionRepository;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * La sincronización incremental de punta a punta (evidencias 2 y 3 del enunciado §11:
 * <em>"una actualizacion incremental del catalogo"</em> y <em>"recuperacion mediante snapshot
 * luego de una discontinuidad"</em>).
 *
 * <p><strong>Qué se monta y por qué.</strong> PostgreSQL 16 en Testcontainers con el
 * changelog real de Liquibase, y un Redis de verdad con las claves que la cátedra publica. La
 * base es donde queda la copia local y donde se verifica que la versión local avanzó; el Redis
 * es de donde salen los cambios y el estado vigente de cada entidad.
 *
 * <p><strong>Por qué el doble de Redis y no la cátedra real.</strong> La ACL y la red de la
 * cátedra no están disponibles sin la VPN, y el enunciado no exige probarlas contra el
 * entorno del profesor: lo que hay que demostrar es que el servicio aplica lo que le llega.
 * El ACL queda como evidencia local en {@code CatalogChangesRedisTest}.
 *
 * <p><strong>El snapshot se sustituye por un doble.</strong> La reconstrucción completa tiene
 * su propio test de integración, con la cátedra completa; acá lo que se verifica es que
 * <em>se pide</em> y que no se intenta encadenar un incremento que no se puede encadenar.
 */
@SpringBootTest
class SyncIncrementalIntegrationTest {

    private static PostgreSQLContainer postgres;
    private static GenericContainer<?> redis;

    @Autowired
    private SyncIncrementalUseCase incremental;

    @Autowired
    private ProfessionalRepository profesionales;
    @Autowired
    private ProfessionalCategoryRepository categorias;
    @Autowired
    private WeeklyScheduleRepository horarios;
    @Autowired
    private CatalogVersionRepository versiones;

    /** El snapshot completo tiene su propio test; acá solo se verifica que se lo pide. */
    @MockitoBean
    private SyncFullSnapshotUseCase snapshotUseCase;

    /**
     * Apunta al Redis del test sin tocar el bean de producción.
     *
     * <p><strong>Es un bean nuevo y no un reemplazo.</strong> El bean real implementa también
     * el origen del token técnico, y sustituirlo rompería el arranque del servicio de catálogo.
     * Con un bean propio marcado como primario, el lector de cambios usa el Redis del test y
     * todo lo demás sigue siendo el de verdad.
     */
    @TestConfiguration
    static class RedisDelTest {

        @Bean
        @Primary
        CatedraRedisSettingsSource settingsSourceDelTest() {
            return () -> new CatedraRedisSettings(
                    redis.getHost(),
                    redis.getMappedPort(6379),
                    null,
                    null,
                    "catedra:sync",
                    "alumnos:test");
        }
    }

    @BeforeAll
    static void levantarLosDobles() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();

        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();
    }

    @AfterAll
    static void bajarLosDobles() {
        if (redis != null) {
            redis.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("clinics.local.db-password", () -> postgres.getPassword());
        registry.add("spring.liquibase.enabled", () -> "true");
        // El change-log lo declara el perfil dev y este test no lo hereda: sin esto Liquibase
        // cae a su default, que no existe.
        registry.add("spring.liquibase.change-log",
                () -> "classpath:/db/changelog/db.changelog-master.xml");

        // El arranque valida la configuración del JWT: sin una clave pública y una audiencia
        // la aplicación no levanta, y el test no usa tokens pero necesita el servicio arriba.
        registry.add("clinics.security.jwt.public-key-path", TestKeys::writePublicKeyToTempFile);
        registry.add("clinics.security.jwt.audience", () -> TestKeys.AUDIENCE);
        registry.add("clinics.security.jwt.issuer", () -> TestKeys.ISSUER);
        registry.add("clinics.security.jwt.allowed-origins", () -> "https://app.example.edu");
    }

    @BeforeEach
    void preparar() {
        horarios.deleteAllInBatch();
        profesionales.deleteAllInBatch();
        categorias.deleteAllInBatch();
        versiones.deleteAllInBatch();

        try (RedisClient cliente = RedisClient.create(
                "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
                StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            conexion.sync().flushall();
        }
    }

    private void sembrarRedis(String currentVersion, String oldestVersion) {
        try (RedisClient cliente = RedisClient.create(
                "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
                StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            var comandos = conexion.sync();

            comandos.set("catedra:sync:current-version", currentVersion);
            comandos.set("catedra:sync:oldest-available-version", oldestVersion);
            comandos.hset("catedra:sync:metadata", java.util.Map.of(
                    "currentVersion", currentVersion,
                    "oldestAvailableVersion", oldestVersion,
                    "publishedAt", "2026-09-10T13:44:59Z"));

            comandos.hset("catedra:sync:professional-categories", "1", """
                    {"id": 1, "name": "Clinica medica", "description": "Atencion clinica general",
                     "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                     "updatedAt": "2026-09-10T13:40:00Z"}""");
            comandos.hset("catedra:sync:professionals", "15", """
                    {"id": 15, "categoryId": 1, "firstName": "Ana", "lastName": "Gomez",
                     "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                     "updatedAt": "2026-09-10T13:40:00Z"}""");

            comandos.set("catedra:sync:changes:" + currentVersion, """
                    {"version": %s, "changes": {"professionalCategories": [1],
                     "professionals": [15], "weeklySchedules": []}}""".formatted(currentVersion));
        }
    }

    private void registraVersionLocal(long version) {
        CatalogVersionEntity entity = new CatalogVersionEntity();
        entity.setVersion(version);
        entity.setSource("SNAPSHOT");
        entity.setAppliedAt(Instant.now());
        versiones.save(entity);
    }

    @Test
    @DisplayName("aplica el cambio y deja la copia local y la version local al dia")
    void aplicaElCambio() {
        registraVersionLocal(4L);
        sembrarRedis("5", "4");

        long version = incremental.applyIncremental();

        assertThat(version).isEqualTo(5L);

        ProfessionalEntity guardado = profesionales.findById(15L).orElseThrow();
        assertThat(guardado.getFirstName()).isEqualTo("Ana");
        assertThat(guardado.isEnabled()).isTrue();

        // La versión local avanzó: está en el historial y es la más alta.
        assertThat(versiones.findTopByOrderByVersionDesc().orElseThrow().getVersion())
                .isEqualTo(5L);
        assertThat(versiones.findByVersion(5L)).isPresent();
    }

    @Test
    @DisplayName("una baja logica se guarda deshabilitada y no borra la fila")
    void unaBajaLogicaNoBorra() {
        registraVersionLocal(4L);
        sembrarRedis("5", "4");
        // El profesional 15 viene deshabilitado del lado de la cátedra.
        try (RedisClient cliente = RedisClient.create(
                "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
                StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            conexion.sync().hset("catedra:sync:professionals", "15", """
                    {"id": 15, "categoryId": 1, "firstName": "Ana", "lastName": "Gomez",
                     "enabled": false, "createdAt": "2026-07-01T12:00:00Z",
                     "updatedAt": "2026-09-10T13:40:00Z"}""");
        }

        incremental.applyIncremental();

        ProfessionalEntity guardado = profesionales.findById(15L).orElseThrow();
        assertThat(guardado.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("con una discontinuidad pide el snapshot y no intenta encadenar el incremento")
    void conDiscontinuidadPideElSnapshot() {
        // El caso del anexo 18.2: local 3, la ventana empieza en 4.
        registraVersionLocal(3L);
        sembrarRedis("7", "4");
        when(snapshotUseCase.applyFullSnapshot()).thenReturn(7L);

        long version = incremental.applyIncremental();

        assertThat(version).isEqualTo(7L);
        verify(snapshotUseCase).applyFullSnapshot();
        // Y la copia local no se tocó: la reconstrucción es la que la va a dejar bien.
        assertThat(profesionales.findById(15L)).isEmpty();
        assertThat(versiones.findTopByOrderByVersionDesc().orElseThrow().getVersion())
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("sin discontinuidad no pide snapshot")
    void sinDiscontinuidadNoPideSnapshot() {
        registraVersionLocal(4L);
        sembrarRedis("5", "4");

        incremental.applyIncremental();

        verifyNoInteractions(snapshotUseCase);
    }
}
