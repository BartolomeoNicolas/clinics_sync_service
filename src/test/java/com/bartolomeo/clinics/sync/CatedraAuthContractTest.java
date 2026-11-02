package com.bartolomeo.clinics.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException;
import com.bartolomeo.clinics.sync.shared.application.exception.ErrorCode;
import com.bartolomeo.clinics.sync.sync.infrastructure.catedra.CatedraIntegrationAuthAdapter;
import com.bartolomeo.clinics.sync.sync.infrastructure.catedra.CatedraProperties;
import com.bartolomeo.clinics.sync.sync.domain.model.IntegrationConfig;
import com.bartolomeo.clinics.sync.sync.domain.model.ProvisioningStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato del cliente de autenticación con la cátedra: anexo §5.2, §5.3 y §6.
 *
 * <p>Levanta un WireMock con la forma exacta de la cátedra y le habla el adaptador real, sin
 * Spring: lo que se prueba acá es el cable —rutas, body, cabecera, mapeo y errores—, no el
 * arranque del contexto. Los criterios del issue que se cubren:
 *
 * <ul>
 *   <li>el token nunca aparece en los mensajes de error;</li>
 *   <li>un 401 de la cátedra es un fallo propio distinguible ({@code catedra-rejected}) de una
 *       falla de red ({@code catedra-unavailable});</li>
 *   <li>el {@code groupId} sale de la integración que devuelve la cátedra —que está atada al
 *       token— y nunca de la configuración local;</li>
 *   <li>el token se cachea en memoria y no se pide de nuevo mientras siga vigente.</li>
 * </ul>
 *
 * <p>No toca la red real de la cátedra: ninguna prueba depende de la VPN del profesor
 * (enunciado §10.1).
 */
class CatedraAuthContractTest {

    private static final String TECHNICAL_USER = "nbartolomeo-prog2";
    private static final String TECHNICAL_PASSWORD = "clave-tecnica-de-prueba";
    /** El groupId con el que se registró la cuenta, a propósito distinto del que devuelve la cátedra. */
    private static final String LOCAL_GROUP_ID = "otro-grupo-local";
    private static final String CATEDRA_GROUP_ID = "proyecto-juan-perez";

    private static WireMockServer catedra;

    @BeforeAll
    static void levantarLaCatedra() {
        catedra = new WireMockServer(wireMockConfig().dynamicPort());
        catedra.start();
    }

    @AfterAll
    static void bajarLaCatedra() {
        catedra.stop();
    }

    @BeforeEach
    void limpiarStubs() {
        catedra.resetAll();
    }

    // ------------------------------------------------------- flujo normal

    @Test
    @DisplayName("sin token configurado hace login y devuelve la integración de la cátedra")
    void sinTokenConfiguradoHaceLogin() {
        stubDeLoginOk(tokenVigente(), CATEDRA_GROUP_ID);

        IntegrationConfig integracion = adaptadorCon(propertiesProvisteado()).currentIntegration();

        // El groupId es el de la respuesta de la cátedra, que está atada al token. El de las
        // properties locales es el con el que se registró la cuenta: no es la autoridad (§6).
        assertThat(integracion.groupId()).isEqualTo(CATEDRA_GROUP_ID);
        assertThat(integracion.redisHost()).isEqualTo("redis.catedra.internal");
        assertThat(integracion.redisReadNamespace()).isEqualTo("catedra:sync:*");
        assertThat(integracion.provisioningStatus()).isEqualTo(ProvisioningStatus.PROVISIONED);

        // §5.2: el body lleva usuario, contraseña y rememberMe para la vigencia larga.
        catedra.verify(postRequestedFor(urlEqualTo("/api/authenticate"))
                .withRequestBody(equalToJson("""
                        {"username": "%s", "password": "%s", "rememberMe": true}"""
                        .formatted(TECHNICAL_USER, TECHNICAL_PASSWORD))));
        catedra.verify(0, getRequestedFor(urlEqualTo("/api/student/integration")));
    }

    @Test
    @DisplayName("un token configurado y vigente se usa sin volver a loguearse")
    void tokenVigenteSeUsaSinLogin() {
        String token = tokenVigente();
        CatedraProperties properties = propertiesProvisteado();
        properties.setTechnicalJwt(token);
        catedra.stubFor(get(urlEqualTo("/api/student/integration"))
                .willReturn(okJson(integrationJson(CATEDRA_GROUP_ID))));

        IntegrationConfig integracion = adaptadorCon(properties).currentIntegration();

        assertThat(integracion.groupId()).isEqualTo(CATEDRA_GROUP_ID);
        catedra.verify(0, postRequestedFor(urlEqualTo("/api/authenticate")));
        // §5.3: la integración se pide con el bearer del JWT técnico.
        catedra.verify(getRequestedFor(urlEqualTo("/api/student/integration"))
                .withHeader("Authorization", equalTo("Bearer " + token)));
    }

    @Test
    @DisplayName("el token se cachea en memoria: tres consultas hacen un solo login")
    void elTokenSeCacheaEnMemoria() {
        stubDeLoginOk(tokenVigente(), CATEDRA_GROUP_ID);
        CatedraIntegrationAuthAdapter adaptador = adaptadorCon(propertiesProvisteado());

        adaptador.currentIntegration();
        adaptador.currentIntegration();
        adaptador.currentIntegration();

        catedra.verify(1, postRequestedFor(urlEqualTo("/api/authenticate")));
    }

    // --------------------------------------------------- vigencia del token

    @Test
    @DisplayName("un token vencido no se manda: se pide uno nuevo directamente")
    void tokenVencidoNiSeManda() {
        CatedraProperties properties = propertiesProvisteado();
        properties.setTechnicalJwt(tokenQueVenceEn(Instant.now().minusSeconds(3_600).getEpochSecond()));
        stubDeLoginOk(tokenVigente(), CATEDRA_GROUP_ID);

        adaptadorCon(properties).currentIntegration();

        // Si el vencido hubiera viajado, la cátedra habría contestado 401 y habría habido un
        // round-trip de más: se corta antes, con el exp.
        catedra.verify(0, getRequestedFor(urlEqualTo("/api/student/integration")));
        catedra.verify(1, postRequestedFor(urlEqualTo("/api/authenticate")));
    }

    @Test
    @DisplayName("un token por vencer se renueva antes de vencer, no después")
    void tokenPorVencerSeRenuevaAntes() {
        // 30 segundos: dentro del margen de renovación. Con un token real, esperar a que venza
        // costaría un 401 evitable.
        CatedraProperties properties = propertiesProvisteado();
        properties.setTechnicalJwt(tokenQueVenceEn(Instant.now().plusSeconds(30).getEpochSecond()));
        stubDeLoginOk(tokenVigente(), CATEDRA_GROUP_ID);

        adaptadorCon(properties).currentIntegration();

        catedra.verify(0, getRequestedFor(urlEqualTo("/api/student/integration")));
        catedra.verify(1, postRequestedFor(urlEqualTo("/api/authenticate")));
    }

    @Test
    @DisplayName("si la cátedra rechaza el token, se cae al login y se recupera")
    void tokenRechazadoCaeAlLogin() {
        CatedraProperties properties = propertiesProvisteado();
        properties.setTechnicalJwt(tokenVigente());
        catedra.stubFor(get(urlEqualTo("/api/student/integration"))
                .willReturn(aResponse().withStatus(401)));
        stubDeLoginOk(tokenVigente(), CATEDRA_GROUP_ID);

        IntegrationConfig integracion = adaptadorCon(properties).currentIntegration();

        // Un token que parecía vigente y la cátedra lo rechaza (revocado, o caché vieja tras
        // un reinicio): no se rinde, pide uno nuevo y sigue.
        assertThat(integracion.groupId()).isEqualTo(CATEDRA_GROUP_ID);
        catedra.verify(1, getRequestedFor(urlEqualTo("/api/student/integration")));
        catedra.verify(1, postRequestedFor(urlEqualTo("/api/authenticate")));
    }

    // ------------------------------------------------------------- errores

    @Test
    @DisplayName("las credenciales rechazadas son un fallo propio, distinto de la red")
    void credencialesRechazadasSeDistinguen() {
        catedra.stubFor(post(urlEqualTo("/api/authenticate")).willReturn(aResponse().withStatus(401)));

        ApplicationException rechazo =
                excepcionDe(() -> adaptadorCon(propertiesProvisteado()).currentIntegration());

        assertThat(rechazo.getErrorCode()).isEqualTo(ErrorCode.CATEDRA_REJECTED);

        // La red caída es otro caso, con otro código: un reintento puede arreglar una, no la
        // otra, y por eso el cliente y el operador tienen que distinguirlas.
        CatedraProperties sinCatedra = propertiesProvisteado();
        sinCatedra.setBaseUrl("http://127.0.0.1:9"); // nadie escucha ahí

        ApplicationException red = excepcionDe(() -> adaptadorCon(sinCatedra).currentIntegration());

        assertThat(red.getErrorCode()).isEqualTo(ErrorCode.CATEDRA_UNAVAILABLE);
    }

    @Test
    @DisplayName("ni el token ni la contraseña aparecen en el mensaje de error")
    void elErrorNoFiltraSecretos() {
        String token = tokenVigente();
        CatedraProperties properties = propertiesProvisteado();
        properties.setTechnicalJwt(token);
        catedra.stubFor(get(urlEqualTo("/api/student/integration"))
                .willReturn(aResponse().withStatus(401)));
        catedra.stubFor(post(urlEqualTo("/api/authenticate")).willReturn(aResponse().withStatus(401)));

        ApplicationException error =
                excepcionDe(() -> adaptadorCon(properties).currentIntegration());

        assertThat(error.getMessage())
                .doesNotContain(token)
                .doesNotContain(TECHNICAL_PASSWORD)
                .doesNotContain("eyJ"); // el prefijo de todo JWT
    }

    @Test
    @DisplayName("sin cuenta registrada falla claro y no le pega a la cátedra")
    void sinCuentaRegistradaNoSeLlama() {
        CatedraProperties sinRegistrar = propertiesProvisteado();
        sinRegistrar.setProvisioningStatus(ProvisioningStatus.PENDING);

        ApplicationException error =
                excepcionDe(() -> adaptadorCon(sinRegistrar).currentIntegration());

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CATEDRA_UNAVAILABLE);
        assertThat(error.getMessage()).contains("no está registrada");
        catedra.verify(0, anyRequestedFor(anyUrl()));
    }

    // ------------------------------------------------------- robustez

    @Test
    @DisplayName("una respuesta con campos desconocidos no rompe la sincronización")
    void respuestaConCamposDesconocidosNoRompe() {
        CatedraProperties properties = propertiesProvisteado();
        properties.setTechnicalJwt(tokenVigente());
        catedra.stubFor(get(urlEqualTo("/api/student/integration")).willReturn(okJson("""
                {
                  "groupId": "%s",
                  "redisHost": "redis.catedra.internal",
                  "redisPort": 6379,
                  "redisReadNamespace": "catedra:sync:*",
                  "provisioningStatus": "PROVISIONED",
                  "campoNuevoDeLaCatedra": {"anidado": true},
                  "otroCampo": 42
                }
                """.formatted(CATEDRA_GROUP_ID))));

        IntegrationConfig integracion = adaptadorCon(properties).currentIntegration();

        assertThat(integracion.groupId()).isEqualTo(CATEDRA_GROUP_ID);
    }

    @Test
    @DisplayName("una integración sin groupId no se acepta: no se puede saber con qué cuenta se trabaja")
    void integracionSinGrupoIdSeRechaza() {
        CatedraProperties properties = propertiesProvisteado();
        properties.setTechnicalJwt(tokenVigente());
        catedra.stubFor(get(urlEqualTo("/api/student/integration")).willReturn(okJson("""
                {"redisHost": "redis.catedra.internal", "redisPort": 6379, "provisioningStatus": "PROVISIONED"}""")));

        ApplicationException error =
                excepcionDe(() -> adaptadorCon(properties).currentIntegration());

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CATEDRA_UNAVAILABLE);
        assertThat(error.getMessage()).contains("sin groupId");
    }

    // --------------------------------------------------------- soporte

    /** Corre la llamada y devuelve la excepción que esperábamos. Sin depender de la API de AssertJ. */
    private static ApplicationException excepcionDe(Runnable llamada) {
        try {
            llamada.run();
        } catch (ApplicationException esperada) {
            return esperada;
        }
        throw new AssertionError("se esperaba una ApplicationException y no se lanzó ninguna");
    }

    private static CatedraIntegrationAuthAdapter adaptadorCon(CatedraProperties properties) {
        ObjectMapper mapper = JsonMapper.builder().build();
        return new CatedraIntegrationAuthAdapter(properties, mapper);
    }

    private static CatedraProperties propertiesProvisteado() {
        CatedraProperties properties = new CatedraProperties();
        properties.setBaseUrl(catedra.baseUrl());
        properties.setGroupId(LOCAL_GROUP_ID);
        properties.setTechnicalUser(TECHNICAL_USER);
        properties.setTechnicalPassword(TECHNICAL_PASSWORD);
        properties.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        return properties;
    }

    private static void stubDeLoginOk(String token, String groupId) {
        catedra.stubFor(post(urlEqualTo("/api/authenticate")).willReturn(okJson("""
                {"id_token": "%s", "integration": %s}""".formatted(token, integrationJson(groupId)))));
    }

    private static String integrationJson(String groupId) {
        return """
                {
                  "groupId": "%s",
                  "redisHost": "redis.catedra.internal",
                  "redisPort": 6379,
                  "redisUsername": "grp_%s",
                  "redisPassword": "secreto-redis-de-prueba",
                  "redisReadNamespace": "catedra:sync:*",
                  "redisWriteNamespace": "alumnos:%s:*",
                  "kafkaBootstrapServers": "kafka.catedra.internal:9092",
                  "kafkaConsumerGroupId": "alumnos-%s",
                  "kafkaCatalogTopic": "catedra.catalog.%s",
                  "kafkaAppointmentActionsTopic": "alumnos.turnos.acciones.%s",
                  "kafkaAppointmentPhoneTopic": "catedra.turnos.telefono.%s",
                  "provisioningStatus": "PROVISIONED"
                }
                """.formatted(groupId, groupId, groupId, groupId, groupId, groupId, groupId, groupId);
    }

    private static String tokenVigente() {
        return tokenQueVenceEn(Instant.now().plusSeconds(365L * 24 * 60 * 60).getEpochSecond());
    }

    /**
     * JWT con el vencimiento que haga falta.
     *
     * <p>La firma es de mentira y no importa: el adaptador solo decodifica el payload para leer
     * {@code exp}, porque quien verifica la firma es la cátedra. Los claims de más que trae
     * ({@code sub}, {@code authorities}) también sirven para comprobar que solo se lee lo que
     * hace falta.
     */
    private static String tokenQueVenceEn(long epochSeconds) {
        String header = base64url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64url(
                "{\"sub\":\"" + TECHNICAL_USER + "\",\"exp\":" + epochSeconds
                        + ",\"authorities\":[\"ROLE_STUDENT_CLIENT\"]}");
        return header + "." + payload + ".firma-de-mentira";
    }

    private static String base64url(String texto) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}
