package com.bartolomeo.clinics.sync.catalog.infrastructure.catedra;

import java.time.Duration;
import java.util.List;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogSnapshot;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogSnapshotPort;
import com.bartolomeo.clinics.sync.shared.application.exception.CatedraUnavailableException;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraProperties;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraTokenSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador de {@link CatalogSnapshotPort}: pide el snapshot completo a la cátedra por HTTP
 * (anexo §7).
 *
 * <p><strong>El token lo toma prestado, no lo tiene.</strong> La sesión técnica es una sola por
 * aplicación y la administra el adaptador de autenticación del contexto {@code sync}; éste la
 * lee a través de {@link CatedraTokenSource}, que está en {@code shared} justamente para que
 * {@code catalog} no tenga que depender de {@code sync}. El JWT nunca pasa por el dominio.
 *
 * <p><strong>Reintentos acotados, y solo porque la operación es idempotente.</strong> Pedir el
 * snapshot dos veces devuelve lo mismo y no deja efecto, así que un fallo transitorio se puede
 * repetir sin riesgo. No es el caso de crear un hold, que no se reintenta así. Lo que <em>no</em>
 * se reintenta es un 4xx: si la cátedra respondió que la petición está mal, pegarle de nuevo
 * con la misma no va a cambiar la respuesta.
 *
 * <p><strong>Un solo reintento por token.</strong> Un 401 con un token que parecía vigente
 * significa que quedó viejo (revocado, o una caché de antes del reinicio). Se descarta con
 * {@link CatedraTokenSource#invalidate()} y se vuelve a intentar una vez con uno nuevo; si el
 * segundo también recibe 401, el problema no es el token y no se insiste.
 */
@Component
public class CatedraCatalogSnapshotAdapter implements CatalogSnapshotPort {

    private static final String SNAPSHOT_PATH = "/api/synchronization/snapshot";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /** Intentos máximos ante fallos transitorios, el primero incluido. */
    private static final int MAX_ATTEMPTS = 3;

    /** Espera base del backoff; se multiplica por el número de intento (250 ms, 500 ms). */
    private static final Duration BASE_BACKOFF = Duration.ofMillis(250);

    private final CatedraProperties properties;
    private final ObjectMapper objectMapper;
    private final CatedraTokenSource tokenSource;
    private final RestClient client;

    public CatedraCatalogSnapshotAdapter(
            CatedraProperties properties,
            ObjectMapper objectMapper,
            CatedraTokenSource tokenSource) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tokenSource = tokenSource;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public CatalogSnapshot fetchSnapshot() {
        try {
            return getWithRetry(tokenSource.currentToken());
        } catch (HttpClientErrorException.Unauthorized primera) {
            // El token parecía vigente y no lo era (revocado, o una caché de antes del
            // reinicio). Se descarta y se reintenta una sola vez con uno nuevo; repetir el
            // snapshot no deja efecto porque es idempotente.
            tokenSource.invalidate();
            try {
                return getWithRetry(tokenSource.currentToken());
            } catch (HttpClientErrorException.Unauthorized segunda) {
                // Ya se invalidó y se pidió un token nuevo, y volvió a ser rechazado: el
                // problema no es el token y no se insiste. Sin este atrape, la excepción
                // cruda de Spring se escaparía sin traducir a la del contrato.
                throw new CatedraUnavailableException(
                        "La cátedra rechazó el token técnico en GET " + SNAPSHOT_PATH + ".",
                        segunda);
            }
        }
    }

    /**
     * Pide el snapshot reintentando lo transitorio.
     *
     * <p>Toda salida del {@code try} es o un resultado o una excepción lanzada, así que el
     * final del método solo se alcanza cuando se agotaron los intentos.
     */
    private CatalogSnapshot getWithRetry(String token) {
        RestClientException ultimo = null;

        for (int intento = 1; intento <= MAX_ATTEMPTS; intento++) {
            try {
                return getOnce(token);
            } catch (HttpClientErrorException.Unauthorized e) {
                throw e; // lo resuelve fetchSnapshot: token nuevo y un solo reintento
            } catch (RestClientException e) {
                if (!reintentable(e)) {
                    throw toApplicationException(e);
                }
                ultimo = e;
                if (intento < MAX_ATTEMPTS) {
                    esperar(intento);
                }
            }
        }

        throw new CatedraUnavailableException(
                "La cátedra no respondió a GET " + SNAPSHOT_PATH + " en " + MAX_ATTEMPTS
                        + " intentos.",
                ultimo);
    }

    /** Una sola petición, sin clasificar el fallo: eso lo hace el llamador. */
    private CatalogSnapshot getOnce(String token) {
        String body = client.get()
                .uri(SNAPSHOT_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(String.class);

        return toDomain(parse(body, CatedraSnapshotResponse.class));
    }

    /**
     * Si vale la pena volver a intentar.
     *
     * <p>Solo lo transitorio: un 5xx o un fallo de red/tiempo. Un 4xx no, porque la respuesta
     * depende de lo que pedimos y no de la suerte de la conexión.
     */
    private static boolean reintentable(RestClientException e) {
        return e instanceof HttpServerErrorException || e instanceof ResourceAccessException;
    }

    /** Traduce un fallo ya clasificado como no reintentable a la excepción de aplicación. */
    private static RuntimeException toApplicationException(RestClientException e) {
        if (e instanceof HttpClientErrorException cliente) {
            return new CatedraUnavailableException(
                    "La cátedra respondió " + cliente.getStatusCode().value() + " en GET "
                            + SNAPSHOT_PATH + ".");
        }
        return new CatedraUnavailableException(
                "No se pudo consultar el snapshot a la cátedra.", e);
    }

    /** Espera progresiva entre intentos. La interrupción corta la espera y se propaga. */
    private static void esperar(int intento) {
        try {
            Thread.sleep(BASE_BACKOFF.toMillis() * intento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatedraUnavailableException(
                    "Se interrumpió la espera del reintento contra la cátedra.", e);
        }
    }

    /** JSON a DTO, con un mensaje que dice qué falló sin repetir el cuerpo. */
    private CatedraSnapshotResponse parse(String body, Class<CatedraSnapshotResponse> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (RuntimeException e) {
            throw new CatedraUnavailableException(
                    "La cátedra respondió algo que no se pudo entender en GET " + SNAPSHOT_PATH
                            + ".", e);
        }
    }

    /** Convierte el DTO a dominio, normalizando las colecciones ausentes a vacío. */
    private CatalogSnapshot toDomain(CatedraSnapshotResponse response) {
        if (response == null) {
            throw new CatedraUnavailableException(
                    "La cátedra devolvió un snapshot sin cuerpo en GET " + SNAPSHOT_PATH + ".");
        }

        return new CatalogSnapshot(
                response.snapshotVersion(),
                response.generatedAt(),
                vacioSiNulo(response.professionalCategories()).stream().map(this::categoria).toList(),
                vacioSiNulo(response.professionals()).stream().map(this::profesional).toList(),
                vacioSiNulo(response.weeklySchedules()).stream().map(this::horario).toList());
    }

    /**
     * Una colección ausente es una colección vacía.
     *
     * <p>El contrato la trae siempre, pero una fila que no viene no debería tumbar el mapeo:
     * no hay nada que aplicar, que es exactamente lo que hace el caso de uso con una lista
     * vacía.
     */
    private static <T> List<T> vacioSiNulo(List<T> source) {
        return source == null ? List.of() : source;
    }

    private ProfessionalCategory categoria(CatedraProfessionalCategoryResponse r) {
        return new ProfessionalCategory(
                r.id(), r.name(), r.description(), r.enabled(), r.createdAt(), r.updatedAt());
    }

    private Professional profesional(CatedraProfessionalResponse r) {
        return new Professional(
                r.id(), r.categoryId(), r.firstName(), r.lastName(), r.enabled(),
                r.createdAt(), r.updatedAt());
    }

    private WeeklySchedule horario(CatedraWeeklyScheduleResponse r) {
        return new WeeklySchedule(
                r.id(), r.professionalId(), r.dayOfWeek(), r.startTime(), r.endTime(),
                r.slotDurationMinutes(), r.enabled(), r.createdAt(), r.updatedAt());
    }
}
