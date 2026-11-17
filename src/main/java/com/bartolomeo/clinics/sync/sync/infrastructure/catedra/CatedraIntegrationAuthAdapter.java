package com.bartolomeo.clinics.sync.sync.infrastructure.catedra;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.bartolomeo.clinics.sync.shared.application.exception.CatedraRejectedException;
import com.bartolomeo.clinics.sync.shared.application.exception.CatedraUnavailableException;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraProperties;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettings;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettingsSource;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraTokenSource;
import com.bartolomeo.clinics.sync.sync.domain.model.IntegrationConfig;
import com.bartolomeo.clinics.sync.sync.domain.model.ProvisioningStatus;
import com.bartolomeo.clinics.sync.sync.domain.ports.out.IntegrationAuthPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador de {@link IntegrationAuthPort}: habla con la cátedra por HTTP.
 *
 * <p>Implementa el flujo del anexo §5: con las credenciales técnicas obtiene un JWT y la
 * integración vigente ({@code POST /api/authenticate}); con un JWT que ya se tiene, recupera
 * solo la integración ({@code GET /api/student/integration}).
 *
 * <p><strong>Caché del token en memoria.</strong> El JWT vence lejos (un año, si se pidió con
 * {@code rememberMe}), pero no para siempre, y cada llamada a la cátedra necesita uno. Por eso
 * se guarda en memoria junto con la integración que vino con él: se reutiliza mientras siga
 * vigente y se pide otro cuando está por vencer. La caché es deliberadamente volatile y vive
 * solo en este objeto: el token renovado nunca se escribe de vuelta al almacén de secretos, y
 * un reinicio simplemente vuelve a autenticarse.
 *
 * <p><strong>Vigencia conocida sin verificar la firma.</strong> Para saber si un token sigue
 * sirviendo se lee su claim {@code exp} decodificando el payload; la firma no se verifica porque
 * quien la valida es la cátedra, no nosotros. Un token sin {@code exp} no se puede evaluar, y en
 * ese caso no se usa: se pide uno nuevo.
 *
 * <p><strong>El token no se filtra.</strong> No hay ningún log en esta clase, los mensajes de
 * error que arma no lo incluyen, y los records que lo cargan lo enmascaran en {@code toString}.
 * La única manera de verlo sería interceptar el tráfico de red, que es donde tiene que estar.
 *
 * <p><strong>Credenciales vs. red, dos fallos distintos.</strong> Un 401 de {@code /api/authenticate}
 * no es un problema de red: es que la cátedra no acepta lo que mandamos. Eso sale como
 * {@code catedra-rejected}, que un reintento no arregla; cualquier otra falla sale como
 * {@code catedra-unavailable}, que puede ser transitoria.
 *
 * <p><strong>Este cliente es solo de la cátedra.</strong> No se inyecta el {@code RestClient.Builder}
 * global de Spring ni se hereda su configuración: esta clase lo arma con su propia URL base y sus
 * propios timeouts, y nada pensado para otros lados tiene por qué colgarse de él. Lo mismo vale
 * para el JSON: el mismo {@link ObjectMapper} inyectado serializa el body y parsea la respuesta,
 * así que el formato de la negociación lo controla acá y no un converter detectado solo.
 *
 * <p>Cuando un token que parecía vigente es rechazado (401 en la integración), el adaptador no
 * se rinde: cae al login, que pide uno nuevo. Cubre el caso de un token revocado o de una
 * caché que quedó vieja tras un reinicio, y cuesta un request que de todos modos iba a fallar.
 */
@Component
public class CatedraIntegrationAuthAdapter
        implements IntegrationAuthPort, CatedraTokenSource, CatedraRedisSettingsSource {

    private static final String AUTHENTICATE_PATH = "/api/authenticate";
    private static final String INTEGRATION_PATH = "/api/student/integration";

    /**
     * Margen para renovar el token.
     *
     * <p>Se renueva antes de vencer y no cuando venció: si no, una llamada que arranca con un
     * token al borde le pega a la cátedra y recibe un 401 que habría sido evitable con
     * anticipación.
     */
    private static final Duration RENEW_MARGIN = Duration.ofSeconds(60);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final CatedraProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    /**
     * La caché.
     *
     * <p>{@code volatile} porque se lee sin el monitor; se escribe solo dentro de
     * {@link #reload()}.
     */
    private volatile CachedSession session;

    public CatedraIntegrationAuthAdapter(CatedraProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public IntegrationConfig currentIntegration() {
        return usableSession().integration();
    }

    @Override
    public String currentToken() {
        return usableSession().token();
    }

    /**
     * Proyecta la parte de Redis de la configuración vigente.
     *
     * <p>No agrega información: la copia campo por campo desde {@link IntegrationConfig} para
     * que el lector Redis de {@code catalog} pueda conectar sin importar los tipos del dominio
     * de {@code sync}. Si mañana la cátedra agrega un dato de conexión, se suma acá y en el
     * registro de {@code shared}, y ningún contexto se entera.
     */
    @Override
    public CatedraRedisSettings currentRedisSettings() {
        IntegrationConfig config = currentIntegration();
        return new CatedraRedisSettings(
                config.redisHost(),
                config.redisPort(),
                config.redisUsername(),
                config.redisPassword(),
                config.redisReadNamespace(),
                config.redisWriteNamespace());
    }

    @Override
    public void invalidate() {
        // Una sola escritura volatile, sin el monitor: quien llama ya recibió el 401, no hay
        // nada que sincronizar con reload() y retener el monitor acá solo lo frenaría.
        session = null;
    }

    /**
     * Sesión vigente, recargándola si no la hay.
     *
     * <p>El token y la integración llegan juntos en la caché, así que garantizar uno garantiza
     * el otro: es el mismo camino que ya recorría {@link #currentIntegration()}, ahora
     * compartido por las dos caras de esta clase.
     *
     * <p>Reintenta si la sesión sigue ausente, por una carrera que el diseño acepta:
     * {@link #invalidate()} puede correr en otro hilo —al responder un 401— justo después de
     * recargar, y en ese caso toca volver a pedirla en vez de devolver {@code null}.
     */
    private CachedSession usableSession() {
        requireProvisioned();

        CachedSession hit = session;
        for (int intento = 0; !usable(hit) && intento < 2; intento++) {
            reload();
            hit = session;
        }

        if (usable(hit)) {
            return hit;
        }
        throw new CatedraUnavailableException(
                "No se pudo obtener una sesión vigente con la cátedra.");
    }

    /** Si la sesión existe y su token sigue sirviendo para usarlo ahora. */
    private boolean usable(CachedSession candidate) {
        return candidate != null && isUsable(candidate.token());
    }

    /**
     * Recarga el token y la integración. Sincronizado: dos hilos que llegan sin caché tienen que
     * hacer un solo login, no dos.
     */
    private synchronized IntegrationConfig reload() {
        // Mientras esperaba el monitor, otro hilo pudo haber recargado ya.
        CachedSession hit = session;
        if (hit != null && isUsable(hit.token())) {
            return hit.integration();
        }

        // Acá el token de la caché ya se sabe que no sirve (los dos retornos de arriba lo
        // garantizan): lo único que queda por intentar es el configurado en el arranque, si
        // está informado y sigue vigente. Pedir la integración con él ahorra un login.
        String candidate = configuredToken();
        if (candidate != null) {
            try {
                IntegrationConfig integration = fetchIntegration(candidate);
                session = new CachedSession(candidate, integration);
                return integration;
            } catch (HttpClientErrorException.Unauthorized e) {
                // El token dejó de servir de verdad: se cae al login de abajo, que pide uno
                // nuevo con las credenciales.
                session = null;
            }
        }

        return authenticate();
    }

    /** Autentica contra la cátedra y guarda el JWT nuevo con la integración que trae. */
    private IntegrationConfig authenticate() {
        String body;
        try {
            body = client.post()
                    .uri(AUTHENTICATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(new AuthenticateRequest(
                            properties.getTechnicalUser(), properties.getTechnicalPassword(), true)))
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            // El detalle no incluye el body de la cátedra ni lo que mandamos: el problema es
            // de configuración y el mensaje apunta a las variables a revisar.
            throw new CatedraRejectedException(
                    "La cátedra rechazó las credenciales técnicas (401 en POST " + AUTHENTICATE_PATH
                            + "). Hay que revisar CATEDRA_TECHNICAL_USER y CATEDRA_TECHNICAL_PASSWORD.");
        } catch (RestClientException e) {
            throw new CatedraUnavailableException("No se pudo autenticar contra la cátedra.", e);
        }

        CatedraAuthenticationResponse response = parse(body, CatedraAuthenticationResponse.class,
                "POST " + AUTHENTICATE_PATH);

        if (response.idToken() == null || response.idToken().isBlank()) {
            throw new CatedraUnavailableException(
                    "La cátedra no devolvió id_token en POST " + AUTHENTICATE_PATH + ".");
        }

        IntegrationConfig integration = toDomain(response.integration());
        session = new CachedSession(response.idToken(), integration);
        return integration;
    }

    /** Pide la integración vigente con el token que se le pasa. */
    private IntegrationConfig fetchIntegration(String token) {
        String body;
        try {
            body = client.get()
                    .uri(INTEGRATION_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw e; // lo trata quien llamó: el token, no la red, es el problema
            }
            throw new CatedraUnavailableException(
                    "La cátedra respondió " + e.getStatusCode().value() + " en GET " + INTEGRATION_PATH + ".");
        } catch (RestClientException e) {
            throw new CatedraUnavailableException(
                    "No se pudo consultar la integración a la cátedra.", e);
        }

        return toDomain(parse(body, CatedraIntegrationResponse.class, "GET " + INTEGRATION_PATH));
    }

    /** Convierte la respuesta a dominio, chequeando lo mínimo para no arrastrar basura. */
    private IntegrationConfig toDomain(CatedraIntegrationResponse response) {
        if (response == null || response.groupId() == null || response.groupId().isBlank()) {
            throw new CatedraUnavailableException(
                    "La cátedra devolvió una integración sin groupId: no se puede saber con qué cuenta se trabaja.");
        }

        ProvisioningStatus provisioningStatus;
        try {
            // Enum plano y no Jackson: un estado nuevo de la cátedra tiene que producir un
            // mensaje claro, no un fallo críptico de mapeo.
            provisioningStatus = ProvisioningStatus.valueOf(response.provisioningStatus());
        } catch (RuntimeException e) {
            throw new CatedraUnavailableException(
                    "La cátedra devolvió un provisioningStatus que no se reconoce.", e);
        }

        return new IntegrationConfig(
                response.groupId(),
                response.redisHost(),
                response.redisPort(),
                response.redisUsername(),
                response.redisPassword(),
                response.redisReadNamespace(),
                response.redisWriteNamespace(),
                response.kafkaBootstrapServers(),
                response.kafkaConsumerGroupId(),
                response.kafkaCatalogTopic(),
                response.kafkaAppointmentActionsTopic(),
                response.kafkaAppointmentPhoneTopic(),
                provisioningStatus);
    }

    /** JSON a DTO, con un mensaje que dice qué endpoint falló sin repetir el cuerpo. */
    private <T> T parse(String body, Class<T> type, String endpoint) {
        try {
            return objectMapper.readValue(body, type);
        } catch (RuntimeException e) {
            throw new CatedraUnavailableException(
                    "La cátedra respondió algo que no se pudo entender en " + endpoint + ".", e);
        }
    }

    /** Token de las properties, solo si está informado y sigue vigente. */
    private String configuredToken() {
        String configured = properties.getTechnicalJwt();
        if (configured == null || configured.isBlank() || !isUsable(configured)) {
            return null;
        }
        return configured;
    }

    /**
     * Si el token sirve para usarlo ahora.
     *
     * <p>Sin {@code exp} no sirve, y no por desconfianza sino por prudencia: no se puede saber
     * cuándo vence, así que no se arriesga mandarlo a la cátedra.
     */
    private boolean isUsable(String token) {
        Instant expiry = expiresAt(token);
        return expiry != null && expiry.isAfter(Instant.now().plus(RENEW_MARGIN));
    }

    /** Vencimiento del token leyendo su payload. Devuelve {@code null} si no se puede determinar. */
    private Instant expiresAt(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            TechnicalJwtClaims claims = objectMapper.readValue(payload, TechnicalJwtClaims.class);
            return claims.exp() == null ? null : Instant.ofEpochSecond(claims.exp());
        } catch (RuntimeException e) {
            // base64 inválido, JSON inválido: no se puede saber la vigencia, y eso no es un
            // error del usuario, así que se trata como "token no utilizable".
            return null;
        }
    }

    private void requireProvisioned() {
        if (!properties.isProvisioned()) {
            throw new CatedraUnavailableException(
                    "La cuenta técnica de la cátedra todavía no está registrada (provisioning-status="
                            + properties.getProvisioningStatus()
                            + "). Hay que registrarla una sola vez con POST /api/student/register y guardar "
                            + "el usuario y la contraseña en CATEDRA_TECHNICAL_USER y CATEDRA_TECHNICAL_PASSWORD.");
        }
    }

    /** Lo que se le manda a la cátedra. Enmascarado por lo mismo que el resto. */
    private record AuthenticateRequest(String username, String password, boolean rememberMe) {

        /**
         * {@code rememberMe} siempre {@code true} a propósito: el anexo §5.2 dice que la
         * vigencia larga sale de ahí, y un JWT que dura un año ahorra un login por día.
         */
        @Override
        public String toString() {
            return "AuthenticateRequest[username=%s, password=***, rememberMe=%s]".formatted(username, rememberMe);
        }
    }

    /**
     * Solo el claim que importa: el resto del payload es de la cátedra y no nos dice nada.
     *
     * <p>Tolerante a lo desconocido porque un JWT real trae más claims ({@code sub},
     * {@code authorities}, {@code ...}): leer solo {@code exp} no tiene por qué romper porque
     * aparezcan otros.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TechnicalJwtClaims(Long exp) {
    }

    /** Estado en memoria: el token y la integración que vino con él. */
    private record CachedSession(String token, IntegrationConfig integration) {

        @Override
        public String toString() {
            return "CachedSession[token=***, integration=%s]".formatted(integration);
        }
    }
}
