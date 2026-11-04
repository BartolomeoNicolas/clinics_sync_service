package com.bartolomeo.clinics.sync.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.bartolomeo.clinics.sync.support.TestKeys;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.ProcessedEventRepository;
import com.bartolomeo.clinics.sync.sync.infrastructure.persistence.repository.JpaProcessedEventRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * La idempotencia contra una base real (evidencia 9 del enunciado §11: <em>"procesamiento
 * idempotente de un mensaje duplicado"</em>).
 *
 * <p><strong>Por qué hace falta la base y no un mock.</strong> La garantía de que el mismo
 * aviso no se procesa dos veces no está solo en el código que pregunta: está en la clave
 * primaria de {@code processed_event}. Un mock que devuelve "ya está" prueba el código, pero
 * no que la base frene el segundo INSERT, que es lo que pasa cuando dos hilos o dos procesos
 * reciben el mismo aviso a la vez.
 */
@SpringBootTest
class ProcessedEventPersistenceTest {

    private static PostgreSQLContainer postgres;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private JpaProcessedEventRepository jpaRepository;

    @BeforeAll
    static void levantarLaBase() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void bajarLaBase() {
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
    }

    @BeforeEach
    void limpiar() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("un aviso repetido no crea una segunda fila")
    void unAvisoRepetidoNoCreaUnaSegundaFila() {
        processedEvents.save("evento-1", 1, 7L);
        processedEvents.save("evento-1", 1, 8L);

        assertThat(processedEvents.existsByEventId("evento-1")).isTrue();
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("el identificador del evento es la clave primaria: la base no deja dos")
    void elIdentificadorEsLaClavePrimaria() {
        processedEvents.save("evento-2", 1, 7L);

        // El aviso repetido actualiza la fila que ya estaba en vez de agregar otra.
        processedEvents.save("evento-2", 1, 9L);

        assertThat(jpaRepository.count()).isEqualTo(1L);
        assertThat(jpaRepository.findById("evento-2").orElseThrow().getAppliedVersion())
                .isEqualTo(9L);
    }

    @Test
    @DisplayName("avisos distintos se guardan los dos")
    void avisosDistintosSeGuardanLosDos() {
        processedEvents.save("evento-3", 1, 7L);
        processedEvents.save("evento-4", 1, 8L);

        assertThat(processedEvents.existsByEventId("evento-3")).isTrue();
        assertThat(processedEvents.existsByEventId("evento-4")).isTrue();
        assertThat(jpaRepository.count()).isEqualTo(2L);
    }
}
