package com.bartolomeo.clinics.sync;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bartolomeo.clinics.sync.support.TestKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Límites HTTP seguros.
 *
 * <p>Cubre los tres casos que la issue declara como evidencia: firma ajena, token vencido, y
 * token válido. Sube el contexto entero, con la cadena de filtros real, y firma los tokens con
 * RSA de verdad: si algo pasara, sería porque la validación funciona.
 *
 * <p>No toca la base: estos tests son sobre seguridad web, y meter una base real acá los haría
 * lentos sin probar nada más. La persistencia se prueba en sus propios tests.
 */
@SpringBootTest(properties = {
        // Estos tests son sobre límites HTTP: no necesitan base. Se apaga el
        // autoconectado de datos para que el contexto levante sin Postgres.
        // Los tests de persistencia usan Testcontainers y no excluyen esto.
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
@AutoConfigureMockMvc
class SecurityLimitsTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // La clave pública confiable la genera el propio test y se inyecta por ruta.
        registry.add("clinics.security.jwt.public-key-path",
                () -> TestKeys.writePublicKeyToTempFile());
        registry.add("clinics.security.jwt.audience", () -> TestKeys.AUDIENCE);
        registry.add("clinics.security.jwt.issuer", () -> TestKeys.ISSUER);
        registry.add("clinics.security.jwt.allowed-origins", () -> "https://app.example.edu");
        registry.add("clinics.local.db-password", () -> "no-se-usa-en-este-test");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    // ---------------------------------------------------------------- 401

    @Test
    @DisplayName("sin token responde 401 problem+json con el código estable")
    void sinToken() throws Exception {
        mockMvc.perform(get("/probe/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthenticated"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/probe/protected"))
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    @DisplayName("token con firma ajena se rechaza")
    void firmaAjena() throws Exception {
        mockMvc.perform(get("/probe/protected")
                        .header("Authorization", "Bearer " + TestKeys.foreignSignatureToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    @DisplayName("token vencido se rechaza y se distingue del resto")
    void tokenVencido() throws Exception {
        mockMvc.perform(get("/probe/protected")
                        .header("Authorization", "Bearer " + TestKeys.expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token-expired"));
    }

    @Test
    @DisplayName("token con la audiencia equivocada se rechaza")
    void audienciaIncorrecta() throws Exception {
        mockMvc.perform(get("/probe/protected")
                        .header("Authorization", "Bearer " + TestKeys.wrongAudienceToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    // ---------------------------------------------------------------- 200

    @Test
    @DisplayName("con token válido accede")
    void tokenValido() throws Exception {
        mockMvc.perform(get("/probe/protected")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("accessible"));
    }

    @Test
    @DisplayName("salud y documentación son las únicas rutas públicas")
    void rutasPublicas() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // Cualquier otra cosa, aunque no exista, pide token: la regla se declara antes
        // de que exista el endpoint.
        mockMvc.perform(get("/api/catalog/professionals"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ problem+json

    @Test
    @DisplayName("una excepción de negocio devuelve su código, no un 500 genérico")
    void errorDeNegocio() throws Exception {
        mockMvc.perform(get("/probe/business-error")
                        .header("Authorization", "Bearer " + TestKeys.validToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("catalog-unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("el catálogo local todavía no está disponible"));
    }

    @Test
    @DisplayName("una validación inválida devuelve 400 con los errores por campo")
    void validacion() throws Exception {
        mockMvc.perform(post("/probe/validated")
                        .header("Authorization", "Bearer " + TestKeys.validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    // ------------------------------------------------------------------ CORS

    @Test
    @DisplayName("un origen permitido recibe el permiso de CORS")
    void corsOrigenPermitido() throws Exception {
        mockMvc.perform(options("/probe/protected")
                        .header("Origin", "https://app.example.edu")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.edu"));
    }

    @Test
    @DisplayName("un origen no permitido no recibe permiso de CORS")
    void corsOrigenNoPermitido() throws Exception {
        mockMvc.perform(options("/probe/protected")
                        .header("Origin", "https://sitio-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}