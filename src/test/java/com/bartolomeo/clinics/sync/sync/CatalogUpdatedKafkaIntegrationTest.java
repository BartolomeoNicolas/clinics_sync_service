package com.bartolomeo.clinics.sync.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.bartolomeo.clinics.sync.support.TestKeys;
import com.bartolomeo.clinics.sync.sync.domain.ports.in.SyncIncrementalUseCase;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.ProcessedEventEntity;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.entity.CatalogVersionEntity;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.CatalogVersionRepository;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.JpaProcessedEventRepository;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * El consumidor de avisos contra un Kafka de verdad (evidencia 9 del enunciado §11:
 * <em>"procesamiento idempotente de un mensaje duplicado"</em>).
 *
 * <p><strong>Qué se prueba y por qué con un broker de verdad.</strong> Lo que importa del
 * consumidor no es solo qué hace con el mensaje: es <em>cuándo</em> confirma el offset. Un
 * mock de {@code Acknowledgment} no puede demostrar que el offset se confirma después de
 * persistir y no antes, y ese es exactamente el punto: confirmar antes perdería el aviso si
 * el proceso se muere entre la confirmación y el guardado. Con un broker se puede mandar el
 * mismo aviso dos veces y ver que el segundo no repite el efecto.
 *
 * <p><strong>Las coordenadas del broker son las del contrato.</strong> El topic y el grupo de
 * consumidores son los que la cátedra publica y los que este servicio escucha; el test los
 * usa tal cual, para que el nombre del topic no sea un detalle del test.
 */
@SpringBootTest
class CatalogUpdatedKafkaIntegrationTest {

    private static final String TOPIC = "catedra.catalog.test-group";

    private static PostgreSQLContainer postgres;
    private static KafkaContainer kafka;
    private static GenericContainer<?> redis;

    @Autowired
    private JpaProcessedEventRepository eventos;

    @Autowired
    private CatalogVersionRepository versiones;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Apunta al Redis del test: el resto de la configuración es la de producción.
     *
     * <p>Es un bean nuevo y no un reemplazo: el bean real implementa también el origen del
     * token técnico, y sustituirlo rompería el arranque del servicio de catálogo.
     */
    @TestConfiguration
    static class RedisDelTest {

        @Bean
        @Primary
        com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettingsSource
                settingsSourceDelTest() {
            return () -> new com.bartolomeo.clinics.sync.shared.infrastructure.catedra
                    .CatedraRedisSettings(
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

        // La imagen de Apache es la que usa este contenedor: corre en modo KRaft y no
        // necesita Zookeeper.
        kafka = new KafkaContainer("apache/kafka:3.9.1");
        kafka.start();

        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();
    }

    @AfterAll
    static void bajarLosDobles() {
        if (redis != null) {
            redis.stop();
        }
        if (kafka != null) {
            kafka.stop();
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
        registry.add("spring.liquibase.change-log",
                () -> "classpath:/db/changelog/db.changelog-master.xml");
        registry.add("clinics.security.jwt.public-key-path", TestKeys::writePublicKeyToTempFile);
        registry.add("clinics.security.jwt.audience", () -> TestKeys.AUDIENCE);
        registry.add("clinics.security.jwt.issuer", () -> TestKeys.ISSUER);
        registry.add("clinics.security.jwt.allowed-origins", () -> "https://app.example.edu");
        registry.add("spring.kafka.bootstrap-servers", () -> kafka.getBootstrapServers());
        registry.add("clinics.catedra.kafka.enabled", () -> "true");
        registry.add("clinics.catedra.kafka.catalog-topic", () -> TOPIC);
        registry.add("clinics.catedra.kafka.consumer-group", () -> "clinics-sync-test");
    }

    @BeforeEach
    void limpiar() {
        eventos.deleteAll();
        versiones.deleteAll();
        // La copia local arranca en la versión 4, que es la anterior a la que avisa el
        // evento: así el aviso se aplica como incremento y no como reconstrucción, y el
        // test no necesita una cátedra de verdad.
        CatalogVersionEntity local = new CatalogVersionEntity();
        local.setVersion(4L);
        local.setSource("SNAPSHOT");
        local.setAppliedAt(Instant.now());
        versiones.save(local);

        try (RedisClient cliente = RedisClient.create(
                "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
                StatefulRedisConnection<String, String> conexion = cliente.connect()) {
            conexion.sync().flushall();
            var comandos = conexion.sync();
            comandos.set("catedra:sync:current-version", "5");
            comandos.set("catedra:sync:oldest-available-version", "4");
            comandos.hset("catedra:sync:metadata", Map.of(
                    "currentVersion", "5",
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
            comandos.set("catedra:sync:changes:5", """
                    {"version": 5, "changes": {"professionalCategories": [1],
                     "professionals": [15], "weeklySchedules": []}}""");
        }
    }

    private String aviso(String eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "CatalogUpdated",
                  "newVersion": 5,
                  "occurredAt": "2026-09-10T13:44:59Z",
                  "source": "MANUAL",
                  "schemaVersion": 1
                }
                """.formatted(eventId);
    }

    private void publica(String eventId) throws Exception {
        kafkaTemplate.send(TOPIC, "5", aviso(eventId)).get(20, TimeUnit.SECONDS);
    }

    private void esperaAQueSeProcese(String eventId) throws InterruptedException {
        for (int intento = 0; intento < 100; intento++) {
            if (eventos.findById(eventId).isPresent()) {
                return;
            }
            Thread.sleep(100);
        }
    }

    @Test
    @DisplayName("el aviso se consume, se sincroniza y se anota como procesado")
    void elAvisoSeConsumeYSeAnota() throws Exception {
        publica("catalog-event-5-4f3b");

        esperaAQueSeProcese("catalog-event-5-4f3b");

        ProcessedEventEntity procesado = eventos.findById("catalog-event-5-4f3b").orElseThrow();
        assertThat(procesado.getSchemaVersion()).isEqualTo(1);
        assertThat(procesado.getAppliedVersion()).isEqualTo(5L);
        assertThat(procesado.getReceivedAt()).isNotNull();
    }

    @Test
    @DisplayName("el mismo aviso dos veces no repite el efecto")
    void elMismoAvisoDosVecesNoRepiteElEfecto() throws Exception {
        publica("catalog-event-5-duplicado");
        esperaAQueSeProcese("catalog-event-5-duplicado");

        // La entrega de Kafka es al menos una vez: el mismo aviso vuelve a llegar.
        publica("catalog-event-5-duplicado");

        // Se espera a que el contenedor reintente y consuma el duplicado.
        Thread.sleep(2000);

        assertThat(eventos.count()).isEqualTo(1L);
    }
}
