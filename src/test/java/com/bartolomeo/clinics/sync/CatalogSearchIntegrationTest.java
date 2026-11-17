package com.bartolomeo.clinics.sync;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.WeeklyScheduleEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalCategoryRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.WeeklyScheduleRepository;
import com.bartolomeo.clinics.sync.support.TestKeys;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Búsqueda de profesionales sobre la copia local —evidencias 4 y 5 del enunciado §11:
 * <em>"busquedas resueltas sobre datos locales"</em> y <em>"funcionamiento de los filtros
 * obligatorios por categoria, nombre, estado habilitado y disponibilidad"</em>.
 *
 * <p><strong>La prueba de que no se consulta a la cátedra es el WireMock vacío.</strong> No se
 * le pone ningún stub: cualquier petición que llegue queda registrada y el {@code @AfterEach}
 * hace fallar el test. Es la forma más dura de verificar el requisito de §4.1 —<em>"utilizando
 * exclusivamente datos locales"</em>— porque no comprueba que la respuesta venga de la base:
 * comprueba que ni siquiera se intentó ir a buscarla a otro lado. Con un stub que devolviera
 * datos, una fuga sería indistinguible de un acierto.
 *
 * <p>La base es PostgreSQL 16 con Liquibase aplicando el changelog real, igual que en
 * {@link SyncFullSnapshotIntegrationTest}: los filtros se prueban contra las mismas
 * restricciones y el mismo esquema con los que se va a producir, y contra un gestor embebido
 * —prohibido por §3— no se probaría nada de esto.
 *
 * <p>Los datos se cargan en cada test y no comparten estado con otros: el orden en que JUnit
 * decida correrlos no puede cambiar el resultado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogSearchIntegrationTest {

    /** El lunes 3 de noviembre de 2026, para poder pedir disponibilidad por fecha. */
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final LocalDate MARTES = LocalDate.of(2026, 11, 3);

    private static PostgreSQLContainer postgres;
    private static WireMockServer catedra;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalCategoryRepository categorias;
    @Autowired
    private ProfessionalRepository profesionales;
    @Autowired
    private WeeklyScheduleRepository horarios;

    private long catMedica;
    private long ana;
    private long luis;
    private long maria;

    @BeforeAll
    static void levantarLosDobles() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();

        // Sin ningún stub a propósito: ver el criterio de la clase.
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

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("clinics.local.db-password", () -> postgres.getPassword());

        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log",
                () -> "classpath:/db/changelog/db.changelog-master.xml");

        registry.add("clinics.catedra.base-url", () -> catedra.baseUrl());

        registry.add("clinics.security.jwt.public-key-path", TestKeys::writePublicKeyToTempFile);
        registry.add("clinics.security.jwt.audience", () -> TestKeys.AUDIENCE);
        registry.add("clinics.security.jwt.issuer", () -> TestKeys.ISSUER);
        registry.add("clinics.security.jwt.allowed-origins", () -> "https://app.example.edu");
    }

    @BeforeEach
    void cargarElCatalogo() {
        catedra.resetAll();

        horarios.deleteAllInBatch();
        profesionales.deleteAllInBatch();
        categorias.deleteAllInBatch();

        catMedica = guardarCategoria(1L, "Clinica medica", true);
        long kinesiologia = guardarCategoria(2L, "Kinesiologia", true);
        long dermatologia = guardarCategoria(3L, "Dermatologia", false);

        ana = guardarProfesional(15L, catMedica, "Ana", "Gomez", true);
        luis = guardarProfesional(16L, kinesiologia, "Luis", "Perez", true);
        maria = guardarProfesional(17L, catMedica, "Maria", "Diaz", true);
        long juan = guardarProfesional(18L, dermatologia, "Juan", "Rojas", false);

        guardarHorario(21L, ana, DayOfWeek.MONDAY, "09:00", "12:00", 30, true);
        guardarHorario(22L, luis, DayOfWeek.MONDAY, "14:00", "18:00", 60, true);
        // Agenda apagada: no es disponibilidad, aunque el profesional sí esté habilitado.
        guardarHorario(23L, luis, DayOfWeek.TUESDAY, "09:00", "12:00", 30, false);
        guardarHorario(24L, maria, DayOfWeek.TUESDAY, "10:00", "14:00", 45, true);
        guardarHorario(25L, juan, DayOfWeek.MONDAY, "08:00", "10:00", 30, true);
    }

    /**
     * <strong>El criterio del issue, verificado después de cada test.</strong>
     *
     * <p>Cualquier petición al WireMock —aunque fuera solo para autenticarse— hace fallar el
     * test. No hay forma de que una búsqueda se escape hacia la cátedra sin que esto lo note.
     */
    @AfterEach
    void ningunaLlamadaALaCatedra() {
        assertThat(catedra.getAllServeEvents())
                .as("una busqueda no puede pedirle nada a la catedra")
                .isEmpty();
    }

    // ------------------------------------------------------- evidencia 5: filtros

    @Test
    @DisplayName("sin filtros aparecen solo los habilitados, ordenados por apellido")
    void buscarSinFiltrosDevuelveSoloHabilitados() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals")
                        .header("Authorization", "Bearer " + TestKeys.validToken())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items.length()").value(3))
                // Diaz, Gomez, Perez: Juan Rojas queda afuera por estar deshabilitado.
                .andExpect(jsonPath("$.items[0].lastName").value("Diaz"))
                .andExpect(jsonPath("$.items[1].lastName").value("Gomez"))
                .andExpect(jsonPath("$.items[2].lastName").value("Perez"));
    }

    @Test
    @DisplayName("el filtro por categoría acota a los profesionales de esa categoría")
    void filtrarPorCategoria() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("categoryId", String.valueOf(catMedica))
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].lastName").value("Diaz"))
                .andExpect(jsonPath("$.items[1].lastName").value("Gomez"));
    }

    @Test
    @DisplayName("el filtro por nombre coincide con el nombre o el apellido, sin importar mayúsculas")
    void filtrarPorNombre() throws Exception {
        // Por apellido, parcial.
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("name", "go")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].firstName").value("Ana"));

        // Por nombre, en mayúsculas: la búsqueda no distingue caso.
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("name", "ANA")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].lastName").value("Gomez"));

        // Por apellido, completo.
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("name", "diaz")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].firstName").value("Maria"));
    }

    @Test
    @DisplayName("el comodín que el usuario escribe no se interpreta como comodín de SQL")
    void unPorcentajeEnElNombreNoBuscaTodo() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("name", "%")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/catalog/professionals")
                        .param("name", "_")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("enabled=false devuelve solo los deshabilitados, y sin parámetro no aparecen")
    void filtrarPorEstadoHabilitado() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("enabled", "false")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(18))
                .andExpect(jsonPath("$.items[0].enabled").value(false));

        // El mismo filtro pedido explícitamente da el mismo resultado que no pedirlo.
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("enabled", "true")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("disponibilidad se resuelve contra la agenda local, y la agenda apagada no cuenta")
    void filtrarPorDisponibilidad() throws Exception {
        // El lunes trabajan Ana y Luis; Juan también, pero está deshabilitado.
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("availableOn", LUNES.toString())
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].lastName").value("Gomez"))
                .andExpect(jsonPath("$.items[1].lastName").value("Perez"));

        // El martes solo María: Luis tiene agenda ese día pero apagada.
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("availableOn", MARTES.toString())
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].lastName").value("Diaz"));

        // Un domingo no trabaja nadie.
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("availableOn", "2026-11-08")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("los filtros se combinan en vez de pisarse")
    void losFiltrosSeCombinan() throws Exception {
        // Categoría clínica + nombre que coincide con dos + el lunes: queda solo Ana, porque
        // María no trabaja ese día.
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("categoryId", String.valueOf(catMedica))
                        .param("name", "a")
                        .param("availableOn", LUNES.toString())
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].firstName").value("Ana"));
    }

    // ------------------------------------------------------- paginacion

    @Test
    @DisplayName("la paginación corta el conjunto y cuenta lo que queda afuera")
    void paginar() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/catalog/professionals")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].lastName").value("Perez"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("una página fuera de rango responde vacía y no un error")
    void paginaFueraDeRango() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("page", "9")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("los límites de la búsqueda se validan y el cliente sabe cuál falló")
    void limitesDeLaBusqueda() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));

        mockMvc.perform(get("/api/catalog/professionals")
                        .param("size", "500")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-error"));

        mockMvc.perform(get("/api/catalog/professionals")
                        .param("availableOn", "no-es-una-fecha")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("availableOn"));
    }

    // ------------------------------------------------------- evidencia 4: locales

    @Test
    @DisplayName("el detalle trae la ficha y la agenda, y no existe sin agenda no es un 404")
    void detalleDeUnProfesional() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals/{id}", ana)
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("Gomez"))
                .andExpect(jsonPath("$.categoryId").value(catMedica))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.weeklySchedules.length()").value(1))
                .andExpect(jsonPath("$.weeklySchedules[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.weeklySchedules[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.weeklySchedules[0].slotDurationMinutes").value(30));
    }

    @Test
    @DisplayName("un profesional que no está responde 404 con su código estable")
    void detalleInexistente() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals/{id}", 999)
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("not-found"))
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/catalog/professionals/{id}/weekly-schedules", 999)
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    @DisplayName("la ruta del detalle acepta solo números y lo demás es un error del cliente")
    void idMalFormado() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals/{id}", "abc")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    @DisplayName("las categorías vienen ordenadas por nombre, incluidas las deshabilitadas")
    void listadoDeCategorias() throws Exception {
        mockMvc.perform(get("/api/catalog/categories")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Clinica medica"))
                .andExpect(jsonPath("$[1].name").value("Dermatologia"))
                .andExpect(jsonPath("$[1].enabled").value(false))
                .andExpect(jsonPath("$[2].name").value("Kinesiologia"));
    }

    @Test
    @DisplayName("la agenda se devuelve de lunes a domingo, con las reglas apagadas incluidas")
    void agendaSemanal() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals/{id}/weekly-schedules", luis)
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Lunes antes que martes: el orden es del dominio, no del texto en la base.
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[1].dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$[1].enabled").value(false));
    }

    @Test
    @DisplayName("sin token ninguna de estas rutas responde, porque no son públicas")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/catalog/professionals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    // ------------------------------------------------------- datos

    private long guardarCategoria(long id, String nombre, boolean habilitada) {
        ProfessionalCategoryEntity entity = new ProfessionalCategoryEntity();
        entity.setId(id);
        entity.setName(nombre);
        entity.setEnabled(habilitada);
        entity.setCreatedAt(Instant.parse("2026-07-01T12:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-11-02T10:00:00Z"));
        return categorias.save(entity).getId();
    }

    private long guardarProfesional(long id, long categoria, String nombre, String apellido,
            boolean habilitado) {
        ProfessionalEntity entity = new ProfessionalEntity();
        entity.setId(id);
        entity.setCategoryId(categoria);
        entity.setFirstName(nombre);
        entity.setLastName(apellido);
        entity.setEnabled(habilitado);
        entity.setCreatedAt(Instant.parse("2026-07-01T12:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-11-02T10:00:00Z"));
        return profesionales.save(entity).getId();
    }

    private void guardarHorario(long id, long profesional, DayOfWeek dia, String inicio,
            String fin, int duracion, boolean habilitado) {
        WeeklyScheduleEntity entity = new WeeklyScheduleEntity();
        entity.setId(id);
        entity.setProfessionalId(profesional);
        entity.setDayOfWeek(dia.name());
        entity.setStartTime(LocalTime.parse(inicio));
        entity.setEndTime(LocalTime.parse(fin));
        entity.setSlotDurationMinutes(duracion);
        entity.setEnabled(habilitado);
        entity.setCreatedAt(Instant.parse("2026-07-01T12:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-11-02T10:00:00Z"));
        horarios.save(entity);
    }
}
