package com.bartolomeo.clinics.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.infrastructure.catedra.CatedraCatalogSnapshotAdapter;
import com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException;
import com.bartolomeo.clinics.sync.shared.application.exception.ErrorCode;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraProperties;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraTokenSource;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato del snapshot de catálogo con la cátedra: anexo §4 y §7.
 *
 * <p>Levanta un WireMock con la forma exacta que documenta el anexo §7 y le habla el adaptador
 * real, sin Spring: se prueba el cable —ruta, cabecera, parseo de fechas y horas, mapeo al
 * dominio y política de reintentos—, no el arranque del contexto.
 *
 * <p>Los criterios del issue que se cubren acá:
 *
 * <ul>
 *   <li>los instantes se leen en ISO-8601 UTC y las horas aceptan {@code HH:mm} y
 *       {@code HH:mm:ss} (§4);</li>
 *   <li>un snapshot con entidades deshabilitadas se procesa sin perderlas (§7: <em>"devuelve
 *       entidades habilitadas y deshabilitadas"</em>);</li>
 *   <li>los DTOs toleran campos desconocidos, porque la cátedra es de otro equipo;</li>
 *   <li>los reintentos son acotados y solo para lo transitorio: la operación es idempotente,
 *       así que repetirla no deja efecto;</li>
 *   <li>el token nunca aparece en los mensajes de error (§5).</li>
 * </ul>
 *
 * <p>No toca la red real de la cátedra: ninguna prueba depende de la VPN del profesor
 * (enunciado §10.1).
 */
class CatalogSnapshotContractTest {

    private static final String RUTA = "/api/synchronization/snapshot";

    /**
     * Tokens de prueba, y ninguno debe aparecer en un mensaje de error (§5).
     *
     * <p>Son dos porque el reintento tras un 401 tiene que salir con uno distinto: si el
     * adaptador volviera a mandar el mismo, el escenario no llegaría nunca al 200.
     */
    private static final String TOKEN_VIEJO = "token-viejo-que-no-debe-aparecer-en-ningun-mensaje";
    private static final String TOKEN_NUEVO = "token-nuevo-que-no-debe-aparecer-en-ningun-mensaje";

    /** Snapshot del anexo §7, tal cual. */
    private static final String SNAPSHOT_SECCION_7 = """
            {
              "snapshotVersion": 7,
              "generatedAt": "2026-09-10T13:44:59Z",
              "professionalCategories": [
                {
                  "id": 1,
                  "name": "Clinica medica",
                  "description": "Atencion clinica general",
                  "enabled": true,
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:40:00Z"
                }
              ],
              "professionals": [
                {
                  "id": 15,
                  "categoryId": 1,
                  "firstName": "Ana",
                  "lastName": "Gomez",
                  "enabled": true,
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:40:00Z"
                }
              ],
              "weeklySchedules": [
                {
                  "id": 21,
                  "professionalId": 15,
                  "dayOfWeek": "MONDAY",
                  "startTime": "09:00:00",
                  "endTime": "12:00:00",
                  "slotDurationMinutes": 30,
                  "enabled": true,
                  "createdAt": "2026-07-01T12:00:00Z",
                  "updatedAt": "2026-09-10T13:40:00Z"
                }
              ]
            }
            """;

    private static WireMockServer catedra;
    private static TokenSourceFalso tokenSource;

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
        tokenSource = new TokenSourceFalso(TOKEN_VIEJO, TOKEN_NUEVO);
    }

    // ------------------------------------------------------- flujo normal

    @Test
    @DisplayName("el snapshot del anexo §7 se mapea al modelo de dominio completo")
    void snapshotDeLaSeccion7SeMapea() {
        catedra.stubFor(get(urlEqualTo(RUTA)).willReturn(okJson(SNAPSHOT_SECCION_7)));

        CatalogSnapshot snapshot = adaptador().fetchSnapshot();

        assertThat(snapshot.snapshotVersion()).isEqualTo(7L);
        assertThat(snapshot.generatedAt()).isEqualTo(Instant.parse("2026-09-10T13:44:59Z"));

        assertThat(snapshot.professionalCategories()).hasSize(1);
        ProfessionalCategory categoria = snapshot.professionalCategories().get(0);
        assertThat(categoria.id()).isEqualTo(1L);
        assertThat(categoria.name()).isEqualTo("Clinica medica");
        assertThat(categoria.enabled()).isTrue();
        assertThat(categoria.createdAt()).isEqualTo(Instant.parse("2026-07-01T12:00:00Z"));
        assertThat(categoria.updatedAt()).isEqualTo(Instant.parse("2026-09-10T13:40:00Z"));

        Professional profesional = snapshot.professionals().get(0);
        assertThat(profesional.id()).isEqualTo(15L);
        assertThat(profesional.categoryId()).isEqualTo(1L);
        assertThat(profesional.firstName()).isEqualTo("Ana");

        WeeklySchedule horario = snapshot.weeklySchedules().get(0);
        assertThat(horario.id()).isEqualTo(21L);
        assertThat(horario.professionalId()).isEqualTo(15L);
        assertThat(horario.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(horario.slotDurationMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("se pide por GET en la ruta del anexo §7 con el bearer del JWT técnico")
    void pideElSnapshotConElBearer() {
        catedra.stubFor(get(urlEqualTo(RUTA)).willReturn(okJson(SNAPSHOT_SECCION_7)));

        adaptador().fetchSnapshot();

        catedra.verify(getRequestedFor(urlEqualTo(RUTA))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN_VIEJO)));
    }

    @Test
    @DisplayName("las horas aceptan HH:mm y HH:mm:ss, los dos formatos que obliga el §4")
    void lasHorasAceptanLosDosFormatos() {
        catedra.stubFor(get(urlEqualTo(RUTA)).willReturn(okJson("""
                {
                  "snapshotVersion": 7,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "professionalCategories": [],
                  "professionals": [],
                  "weeklySchedules": [
                    {"id": 1, "professionalId": 15, "dayOfWeek": "TUESDAY",
                     "startTime": "09:00", "endTime": "13:00",
                     "slotDurationMinutes": 20, "enabled": true,
                     "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"},
                    {"id": 2, "professionalId": 15, "dayOfWeek": "WEDNESDAY",
                     "startTime": "14:30:00", "endTime": "18:00:00",
                     "slotDurationMinutes": 45, "enabled": true,
                     "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"}
                  ]
                }
                """)));

        List<WeeklySchedule> horarios = adaptador().fetchSnapshot().weeklySchedules();

        assertThat(horarios).hasSize(2);
        // Se normalizan al mismo valor: 09:00 y 09:00:00 son la misma hora de atención.
        assertThat(horarios.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(horarios.get(0).dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(horarios.get(1).startTime()).isEqualTo(LocalTime.of(14, 30, 0));
        assertThat(horarios.get(1).dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    }

    @Test
    @DisplayName("las entidades deshabilitadas se procesan sin perderlas")
    void lasEntidadesDeshabilitadasSeConservan() {
        catedra.stubFor(get(urlEqualTo(RUTA)).willReturn(okJson("""
                {
                  "snapshotVersion": 7,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "professionalCategories": [
                    {"id": 1, "name": "Kinesiologia", "description": "baja logica",
                     "enabled": false,
                     "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"}
                  ],
                  "professionals": [
                    {"id": 15, "categoryId": 1, "firstName": "Ana", "lastName": "Gomez",
                     "enabled": false,
                     "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"}
                  ],
                  "weeklySchedules": [
                    {"id": 21, "professionalId": 15, "dayOfWeek": "MONDAY",
                     "startTime": "09:00:00", "endTime": "12:00:00",
                     "slotDurationMinutes": 30, "enabled": false,
                     "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"}
                  ]
                }
                """)));

        CatalogSnapshot snapshot = adaptador().fetchSnapshot();

        // §7: el snapshot trae habilitadas y deshabilitadas. Filtrarlas acá haría que una baja
        // pareciera una desaparición y rompería el incremental, que referencia por ID.
        assertThat(snapshot.professionalCategories()).hasSize(1);
        assertThat(snapshot.professionals()).hasSize(1);
        assertThat(snapshot.weeklySchedules()).hasSize(1);
        assertThat(snapshot.professionals().get(0).enabled()).isFalse();
        assertThat(snapshot.weeklySchedules().get(0).enabled()).isFalse();
    }

    // --------------------------------------------------------- robustez

    @Test
    @DisplayName("un campo desconocido de la cátedra no tumba la sincronización")
    void unCampoDesconocidoNoRompe() {
        catedra.stubFor(get(urlEqualTo(RUTA)).willReturn(okJson("""
                {
                  "snapshotVersion": 7,
                  "generatedAt": "2026-09-10T13:44:59Z",
                  "campoNuevoDeLaCatedra": {"anidado": true},
                  "professionalCategories": [],
                  "professionals": [],
                  "weeklySchedules": [],
                  "otroCampo": 42
                }
                """)));

        CatalogSnapshot snapshot = adaptador().fetchSnapshot();

        assertThat(snapshot.snapshotVersion()).isEqualTo(7L);
        assertThat(snapshot.professionalCategories()).isEmpty();
    }

    @Test
    @DisplayName("un cuerpo que no se entiende es catedra-unavailable, no un error genérico")
    void unCuerpoIncomprensibleNoRompe() {
        catedra.stubFor(get(urlEqualTo(RUTA)).willReturn(okJson("esto no es un snapshot")));

        ApplicationException error = excepcionDe(() -> adaptador().fetchSnapshot());

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CATEDRA_UNAVAILABLE);
        assertThat(error.getMessage()).contains("no se pudo entender");
        // §5: el token no puede aparecer en mensajes de error, ni en este.
        assertThat(error.getMessage()).doesNotContain(TOKEN_VIEJO).doesNotContain(TOKEN_NUEVO);
    }

    // ------------------------------------------------------- reintentos

    @Test
    @DisplayName("un 5xx se reintenta y a la segunda sale bien")
    void un5xxSeReintenta() {
        catedra.stubFor(get(urlEqualTo(RUTA)).inScenario("reintento")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("falló"));
        catedra.stubFor(get(urlEqualTo(RUTA)).inScenario("reintento")
                .whenScenarioStateIs("falló")
                .willReturn(okJson(SNAPSHOT_SECCION_7)));

        CatalogSnapshot snapshot = adaptador().fetchSnapshot();

        assertThat(snapshot.snapshotVersion()).isEqualTo(7L);
        catedra.verify(2, getRequestedFor(urlEqualTo(RUTA)));
    }

    @Test
    @DisplayName("un 4xx que no es 401 no se reintenta: la respuesta no va a cambiar")
    void un4xxQueNoEs401NoSeReintenta() {
        catedra.stubFor(get(urlEqualTo(RUTA)).willReturn(aResponse().withStatus(404)));

        ApplicationException error = excepcionDe(() -> adaptador().fetchSnapshot());

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CATEDRA_UNAVAILABLE);
        assertThat(error.getMessage()).contains("404");
        // Uno solo: pegarle de nuevo con la misma petición no cambia nada.
        catedra.verify(1, getRequestedFor(urlEqualTo(RUTA)));
    }

    @Test
    @DisplayName("un 401 invalida el token cacheado y reintenta una vez con uno nuevo")
    void un401InvalidaYReintentaUnaVez() {
        catedra.stubFor(get(urlEqualTo(RUTA)).inScenario("token-viejo")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("token-nuevo"));
        catedra.stubFor(get(urlEqualTo(RUTA)).inScenario("token-viejo")
                .whenScenarioStateIs("token-nuevo")
                .willReturn(okJson(SNAPSHOT_SECCION_7)));

        CatalogSnapshot snapshot = adaptador().fetchSnapshot();

        assertThat(snapshot.snapshotVersion()).isEqualTo(7L);
        assertThat(tokenSource.invalidaciones).isEqualTo(1);
        catedra.verify(2, getRequestedFor(urlEqualTo(RUTA)));
        // El reintento no repite el token viejo: invalidate() hizo que pidiera otro.
        catedra.verify(getRequestedFor(urlEqualTo(RUTA))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN_NUEVO)));
    }

    @Test
    @DisplayName("un segundo 401 no se reintenta más: el problema no es el token")
    void unSegundo401NoSeReintentaMas() {
        catedra.stubFor(get(urlEqualTo(RUTA)).willReturn(aResponse().withStatus(401)));

        ApplicationException error = excepcionDe(() -> adaptador().fetchSnapshot());

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CATEDRA_UNAVAILABLE);
        // Dos intentos: el original y el de después de invalidar. No un tercero.
        catedra.verify(2, getRequestedFor(urlEqualTo(RUTA)));
        assertThat(error.getMessage()).doesNotContain(TOKEN_VIEJO).doesNotContain(TOKEN_NUEVO);
    }

    // --------------------------------------------------------- soporte

    private CatedraCatalogSnapshotAdapter adaptador() {
        ObjectMapper mapper = JsonMapper.builder().build();
        return new CatedraCatalogSnapshotAdapter(properties(), mapper, tokenSource);
    }

    private static CatedraProperties properties() {
        CatedraProperties properties = new CatedraProperties();
        properties.setBaseUrl(catedra.baseUrl());
        return properties;
    }

    /** Corre la llamada y devuelve la excepción que esperábamos. */
    private static ApplicationException excepcionDe(Runnable llamada) {
        try {
            llamada.run();
        } catch (ApplicationException esperada) {
            return esperada;
        }
        throw new AssertionError("se esperaba una ApplicationException y no se lanzó ninguna");
    }

    /**
     * Doble de {@link CatedraTokenSource}: devuelve los tokens en orden y cuenta cuántas veces
     * se le avisó que el vencido.
     *
     * <p>Se usa un doble y no el adaptador real de autenticación porque esto prueba el contrato
     * del snapshot, no el de login: mezclarlos haría que un fallo de uno no se pudiera atribuir.
     */
    private static final class TokenSourceFalso implements CatedraTokenSource {

        private final List<String> tokens;
        private int posicion;
        private int invalidaciones;

        private TokenSourceFalso(String... tokens) {
            this.tokens = List.of(tokens);
        }

        @Override
        public String currentToken() {
            return tokens.get(Math.min(posicion, tokens.size() - 1));
        }

        @Override
        public void invalidate() {
            invalidaciones++;
            posicion++;
        }
    }
}
