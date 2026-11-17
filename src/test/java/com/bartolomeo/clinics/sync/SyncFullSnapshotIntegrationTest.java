package com.bartolomeo.clinics.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.WeeklyScheduleEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalCategoryRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.WeeklyScheduleRepository;
import com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException;
import com.bartolomeo.clinics.sync.shared.application.exception.ErrorCode;
import com.bartolomeo.clinics.sync.support.TestKeys;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncFullSnapshotUseCase;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncDiagnosticsPort;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.SyncLockPort;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.SyncErrorEntity;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.CatalogVersionRepository;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.SyncErrorRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Sincronización completa de punta a punta: la cátedra, el caso de uso y la base real
 * (evidencia 1 del enunciado §11: <em>"una inicializacion desde snapshot"</em>).
 *
 * <p><strong>Qué se monta y por qué.</strong> PostgreSQL 16 en Testcontainers con Liquibase
 * aplicando el changelog real, y un WireMock haciendo de cátedra. Es la combinación que pide
 * PLAN.md §10 —integración con Testcontainers, cátedra con WireMock— y la única que puede
 * demostrar lo que este issue tiene que demostrar: que la transacción entera se confirma o se
 * revierte entera contra una base de verdad, con las restricciones de clave ajena y de chequeo
 * puestas. Con una base embebida o con el esquema creado por Hibernate no se probaría nada de
 * eso: el enunciado §3 además prohíbe las bases embebidas.
 *
 * <p><strong>Qué NO prueba.</strong> Que la ACL real de la cátedra o su red estén bien: para eso
 * está la VPN y sigue pendiente. Acá la cátedra es un doble, y lo que se verifica es que el
 * servicio reacciona bien ante lo que ella devuelve.
 *
 * <p>La base se limpia antes de cada test para que los casos no dependan del orden en que los
 * corra JUnit: cada uno arranca de un catálogo vacío y monta lo que necesita.
 */
@SpringBootTest
class SyncFullSnapshotIntegrationTest {

    private static PostgreSQLContainer postgres;
    private static WireMockServer catedra;

    @Autowired
    private SyncFullSnapshotUseCase sincronizacion;

    @Autowired
    private ProfessionalCategoryRepository categorias;
    @Autowired
    private ProfessionalRepository profesionales;
    @Autowired
    private WeeklyScheduleRepository horarios;
    @Autowired
    private CatalogVersionRepository versiones;
    @Autowired
    private SyncErrorRepository errores;

    @BeforeAll
    static void levantarLosDobles() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();

        catedra = new WireMockServer(wireMockConfig().dynamicPort());
        catedra.start();
    }

    @AfterAll
    static void bajarLosDobles() {
        if (catedra != null) {
            catedra.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    /**
     * La base apunta al contenedor y la cátedra al doble.
     *
     * <p>Todo por acá y no por properties estáticas porque las dos URLs se saben recién cuando
     * los contenedores arrancan. El resto —secretos, JWT, Liquibase encendido— también, para
     * que no haya una configuración del test repartida en dos lugares.
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        // La validación de arranque pide este secreto aunque el test no lo use como secreto.
        registry.add("clinics.local.db-password", () -> postgres.getPassword());

        // El perfil test trae Liquibase apagado: acá el esquema es exactamente el de producción.
        // El change-log también hace falta declararlo: lo pone el perfil dev y el test no lo
        // hereda, así que sin esto Liquibus cae a su default `.yaml`, que no existe.
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log",
                () -> "classpath:/db/changelog/db.changelog-master.xml");

        registry.add("clinics.catedra.base-url", () -> catedra.baseUrl());

        registry.add("clinics.security.jwt.public-key-path", TestKeys::writePublicKeyToTempFile);
        registry.add("clinics.security.jwt.audience", () -> TestKeys.AUDIENCE);
        registry.add("clinics.security.jwt.issuer", () -> TestKeys.ISSUER);
        registry.add("clinics.security.jwt.allowed-origins", () -> "https://app.example.edu");
    }

    /**
     * El lock y el diagnóstico, sin Redis.
     *
     * <p>Este test mide el snapshot contra la cátedra (un doble de WireMock) y una base real;
     * el Redis de la cátedra no está y no hace falta: el lock y el registro de estado tienen su
     * propia prueba. Con un bean propio marcado como primario, el resto del arranque es el de
     * producción.
     */
    @TestConfiguration
    static class SinRedisDeLaCatedra {

        @Bean
        @Primary
        LockYDiagnostico stubDelLockYElDiagnostico() {
            return new LockYDiagnostico();
        }
    }

    /** Lock y diagnóstico que no hacen nada: el test no tiene Redis. */
    static class LockYDiagnostico implements SyncLockPort, SyncDiagnosticsPort {

        @Override
        public boolean tryAcquire(Duration ttl) {
            return true;
        }

        @Override
        public void release() {
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public void recordLastSync(long version, String outcome, Instant finishedAt,
                String detail) {
        }

        @Override
        public Optional<LastSync> lastSync() {
            return Optional.empty();
        }
    }

    @BeforeEach
    void partirDeCero() {
        catedra.resetAll();
        stubDeLoginOk();

        // Orden inverso a las claves ajenas, igual que en el adaptador.
        horarios.deleteAllInBatch();
        profesionales.deleteAllInBatch();
        categorias.deleteAllInBatch();
        errores.deleteAllInBatch();
        versiones.deleteAllInBatch();
    }

    /**
     * El adaptador de autenticación pide un JWT antes de poder pedir el snapshot.
     *
     * <p>La firma es de mentira y no importa: el adaptador solo decodifica el payload para
     * leer {@code exp} y saber si el token sigue sirviendo, porque quien verifica la firma es
     * la cátedra. Igual que en {@link CatedraAuthContractTest}.
     */
    private void stubDeLoginOk() {
        String token = base64url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}")
                + "." + base64url("{\"sub\":\"tecnico\",\"exp\":"
                        + Instant.now().plusSeconds(365L * 24 * 60 * 60).getEpochSecond() + "}")
                + ".firma-de-mentira";

        catedra.stubFor(post(urlEqualTo("/api/authenticate")).willReturn(okJson("""
                {"id_token": "%s", "integration": {
                  "groupId": "test-group",
                  "redisHost": "redis.catedra.internal",
                  "redisPort": 6379,
                  "redisUsername": "grp_test-group",
                  "redisPassword": "secreto-de-prueba",
                  "redisReadNamespace": "catedra:sync:*",
                  "redisWriteNamespace": "alumnos:test-group:*",
                  "kafkaBootstrapServers": "kafka.catedra.internal:9092",
                  "kafkaConsumerGroupId": "alumnos-test-group",
                  "kafkaCatalogTopic": "catedra.catalog.test-group",
                  "kafkaAppointmentActionsTopic": "alumnos.turnos.acciones.test-group",
                  "kafkaAppointmentPhoneTopic": "catedra.turnos.telefono.test-group",
                  "provisioningStatus": "PROVISIONED"
                }}""".formatted(token))));
    }

    private static String base64url(String texto) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------- evidencia 1

    @Test
    @DisplayName("un catálogo vacío se inicializa desde snapshot con las tres colecciones y su versión")
    void inicializacionDesdeSnapshot() {
        catedra.stubFor(get(urlEqualTo("/api/synchronization/snapshot"))
                .willReturn(okJson(SNAPSHOT_V5)));

        long aplicada = sincronizacion.applyFullSnapshot();

        assertThat(aplicada).isEqualTo(5L);

        // Las tres colecciones quedaron cargadas, con los tipos que el contrato exige.
        List<ProfessionalCategoryEntity> categoriasGuardadas = categorias.findAll();
        assertThat(categoriasGuardadas).hasSize(1);
        assertThat(categoriasGuardadas.get(0).getName()).isEqualTo("Clinica medica");
        assertThat(categoriasGuardadas.get(0).isEnabled()).isTrue();

        List<ProfessionalEntity> profesionalesGuardados = profesionales.findAll();
        assertThat(profesionalesGuardados).hasSize(1);
        assertThat(profesionalesGuardados.get(0).getCategoryId()).isEqualTo(1L);
        assertThat(profesionalesGuardados.get(0).getFirstName()).isEqualTo("Ana");

        List<WeeklyScheduleEntity> horariosGuardados = horarios.findAll();
        assertThat(horariosGuardados).hasSize(1);
        assertThat(horariosGuardados.get(0).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY.name());
        assertThat(horariosGuardados.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(horariosGuardados.get(0).getSlotDurationMinutes()).isEqualTo(30);

        // Y la versión guardada corresponde exactamente a los datos almacenados (§6.1).
        List<CatalogVersionEntity> aplicadas = versiones.findAll();
        assertThat(aplicadas).hasSize(1);
        assertThat(aplicadas.get(0).getVersion()).isEqualTo(5L);
        assertThat(aplicadas.get(0).getSource()).isEqualTo("SNAPSHOT");
        assertThat(aplicadas.get(0).getSnapshotGeneratedAt())
                .isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));

        assertThat(errores.findAll()).isEmpty();
    }

    @Test
    @DisplayName("aplicar el mismo snapshot dos veces produce exactamente el mismo estado")
    void ejecucionRepetidaProduceElMismoEstado() {
        catedra.stubFor(get(urlEqualTo("/api/synchronization/snapshot"))
                .willReturn(okJson(SNAPSHOT_V5)));

        sincronizacion.applyFullSnapshot();
        Instant primeraVez = versiones.findAll().get(0).getAppliedAt();

        sincronizacion.applyFullSnapshot();
        List<CatalogVersionEntity> aplicadas = versiones.findAll();

        // Ni filas de más ni fecha movida: una repetición por timeout no altera nada (§8).
        assertThat(aplicadas).hasSize(1);
        assertThat(aplicadas.get(0).getAppliedAt()).isEqualTo(primeraVez);
        assertThat(categorias.count()).isEqualTo(1);
        assertThat(profesionales.count()).isEqualTo(1);
        assertThat(horarios.count()).isEqualTo(1);
        assertThat(errores.findAll()).isEmpty();
    }

    @Test
    @DisplayName("una copia que no se puede reconstruir deja intacta la anterior y su versión")
    void siLaTransaccionFallaLaCopiaAnteriorQuedaIntacta() {

        // Primera aplicación, que sí funciona.
        catedra.stubFor(get(urlEqualTo("/api/synchronization/snapshot"))
                .willReturn(okJson(SNAPSHOT_V5)));
        sincronizacion.applyFullSnapshot();

        // Después un snapshot que la base no acepta: el profesional apunta a una categoría
        // que no trae. La clave ajena lo revienta al insertar.
        catedra.stubFor(get(urlEqualTo("/api/synchronization/snapshot"))
                .willReturn(okJson(SNAPSHOT_ROTO_V7)));

        assertThatThrownBy(() -> sincronizacion.applyFullSnapshot())
                .isInstanceOf(RuntimeException.class);

        // La copia anterior sigue entera: ni una fila de más, ni una de menos.
        assertThat(categorias.findAll()).singleElement()
                .extracting(ProfessionalCategoryEntity::getName).isEqualTo("Clinica medica");
        assertThat(profesionales.findAll()).singleElement()
                .extracting(ProfessionalEntity::getFirstName).isEqualTo("Ana");
        assertThat(horarios.findAll()).singleElement()
                .extracting(WeeklyScheduleEntity::getProfessionalId).isEqualTo(15L);

        // Y la versión local no avanzó: sigue siendo la que corresponde a esa copia (§6.1).
        List<CatalogVersionEntity> aplicadas = versiones.findAll();
        assertThat(aplicadas).hasSize(1);
        assertThat(aplicadas.get(0).getVersion()).isEqualTo(5L);

        // El fallo sí queda registrado, con la versión que se intentaba aplicar.
        List<SyncErrorEntity> registrados = errores.findAll();
        assertThat(registrados).hasSize(1);
        assertThat(registrados.get(0).getKind()).isEqualTo("snapshot");
        assertThat(registrados.get(0).getAttemptedVersion()).isEqualTo(7L);
        assertThat(registrados.get(0).getDetail()).contains("fk_professional_category");
    }

    @Test
    @DisplayName("si la cátedra no responde no se toca nada y el error queda registrado")
    void siLaCatedraNoRespondeNoSeTocaLaBase() {
        // Sin stub, WireMock responde 404: un 4xx no se reintenta porque la respuesta no va a
        // cambiar, así que el test no se queda esperando backoff.
        assertThatThrownBy(() -> sincronizacion.applyFullSnapshot())
                .isInstanceOf(ApplicationException.class)
                .extracting(excepcion -> ((ApplicationException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.CATEDRA_UNAVAILABLE);

        assertThat(categorias.findAll()).isEmpty();
        assertThat(profesionales.findAll()).isEmpty();
        assertThat(horarios.findAll()).isEmpty();
        assertThat(versiones.findAll()).isEmpty();

        // Todavía no se sabía contra qué versión se trabajaba, y eso se refleja en el registro.
        List<SyncErrorEntity> registrados = errores.findAll();
        assertThat(registrados).hasSize(1);
        assertThat(registrados.get(0).getKind()).isEqualTo("snapshot");
        assertThat(registrados.get(0).getAttemptedVersion()).isNull();
    }

    // ------------------------------------------------------- fixtures

    /**
     * Snapshot de una sola fila por colección, con la forma del anexo §7.
     *
     * <p>Versión 5 porque el test de continuidad necesita una copia vieja que reemplazar.
     */
    private static final String SNAPSHOT_V5 = """
            {
              "snapshotVersion": 5,
              "generatedAt": "2026-11-02T10:00:00Z",
              "professionalCategories": [
                {"id": 1, "name": "Clinica medica", "description": "Atencion clinica general",
                 "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                 "updatedAt": "2026-11-02T10:00:00Z"}
              ],
              "professionals": [
                {"id": 15, "categoryId": 1, "firstName": "Ana", "lastName": "Gomez",
                 "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                 "updatedAt": "2026-11-02T10:00:00Z"}
              ],
              "weeklySchedules": [
                {"id": 21, "professionalId": 15, "dayOfWeek": "MONDAY",
                 "startTime": "09:00:00", "endTime": "12:00:00", "slotDurationMinutes": 30,
                 "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                 "updatedAt": "2026-11-02T10:00:00Z"}
              ]
            }
            """;

    /**
     * Snapshot inconsistente a propósito: el profesional apunta a la categoría 999, que no
     * viene en la lista.
     *
     * <p>Es el caso del enunciado §8 —<em>"datos de entrada invalidos o referencias inexistentes"—</em>
     * y sirve para forzar un fallo de la base sin mocks: quien falla es la restricción real de
     * la tabla, que es exactamente lo que tiene que hacer fallar.
     */
    private static final String SNAPSHOT_ROTO_V7 = """
            {
              "snapshotVersion": 7,
              "generatedAt": "2026-11-02T11:00:00Z",
              "professionalCategories": [
                {"id": 2, "name": "Kinesiologia", "description": "Otra categoria",
                 "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                 "updatedAt": "2026-11-02T11:00:00Z"}
              ],
              "professionals": [
                {"id": 16, "categoryId": 999, "firstName": "Luis", "lastName": "Perez",
                 "enabled": true, "createdAt": "2026-07-01T12:00:00Z",
                 "updatedAt": "2026-11-02T11:00:00Z"}
              ],
              "weeklySchedules": []
            }
            """;
}
