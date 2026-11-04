package com.bartolomeo.clinics.sync.catalog.infrastructure.catedra;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.bartolomeo.clinics.sync.catalog.domain.model.CatalogChanges;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.SyncState;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogChangesPort;
import com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException;
import com.bartolomeo.clinics.sync.shared.application.exception.CatedraUnavailableException;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettings;
import com.bartolomeo.clinics.sync.shared.infrastructure.catedra.CatedraRedisSettingsSource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador de {@link CatalogChangesPort}: lee el estado de sincronización que publica la
 * cátedra en Redis (anexo §14.2 a §14.5).
 *
 * <p><strong>Solo lectura, y no hay forma de que escriba.</strong> La clase no tiene un único
 * método de escritura: no existe código que pueda dejar algo del lado de la cátedra. Es la
 * traducción en código de la ACL del anexo §14.1, que da lectura en {@code catedra:sync:*} y
 * no escritura. Tampoco se lee {@code catedra:holds:*}: ese namespace es interno de la cátedra
 * y se manipula solo por REST.
 *
 * <p><strong>Las claves salen del contrato, no de un string puesto a mano.</strong> El prefijo
 * se deriva de {@code redisReadNamespace}, que la cátedra entrega como patrón glob
 * ({@code catedra:sync:*}); se le quita el {@code :*} y se le agregan los nombres de clave que
 * enumera el anexo §14.2 a §14.5. Si la cátedra cambiara el namespace, cambia en la
 * configuración y no hay código que tocar.
 *
 * <p><strong>Las credenciales vienen de la integración, no de las properties locales</strong>
 * (anexo §14.1): las entrega el objeto {@code integration}, que está atado al JWT técnico. Se
 * leen a través de {@link CatedraRedisSettingsSource}, que está en {@code shared} para que
 * {@code catalog} no tenga que depender del dominio de {@code sync}.
 *
 * <p><strong>Las lecturas de entidades son por lote.</strong> El incremental entrega IDs y no
 * entidades, así que se van a buscar de a muchos con un solo {@code HMGET}: aplicar una
 * versión de tres cambios no justifica traerse el Hash entero.
 *
 * <p>Una clave que no existe no es un fallo: es el estado normal de una versión que todavía no
 * se publicó, y se devuelve vacío.
 */
@Component
public class CatedraCatalogChangesAdapter implements CatalogChangesPort {

    private static final String CURRENT_VERSION_KEY = "current-version";
    private static final String OLDEST_AVAILABLE_VERSION_KEY = "oldest-available-version";
    private static final String METADATA_KEY = "metadata";
    private static final String PROFESSIONAL_CATEGORIES_KEY = "professional-categories";
    private static final String PROFESSIONALS_KEY = "professionals";
    private static final String WEEKLY_SCHEDULES_KEY = "weekly-schedules";
    private static final String CHANGES_KEY_PREFIX = "changes:";

    private final CatedraRedisSettingsSource settingsSource;
    private final ObjectMapper objectMapper;

    /**
     * La conexión abierta, y con qué configuración se abrió.
     *
     * <p>Abrir una conexión de Redis cuesta, así que no se hace por llamada. Se reabre solo si
     * cambiaron los datos de conexión —por un rotación de credenciales—, que es algo que pasa
     * una vez por año y no en el camino crítico.
     */
    private volatile CachedConnection cache;

    public CatedraCatalogChangesAdapter(
            CatedraRedisSettingsSource settingsSource, ObjectMapper objectMapper) {
        this.settingsSource = settingsSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public SyncState syncState() {
        CatedraRedisSettings settings = settingsSource.currentRedisSettings();
        StringRedisTemplate redis = plantilla(settings);

        return enRedis("las claves de versión", () -> {
            // Variable tipada a propósito: encadenar opsForHash().entries(...) hace que la
            // inferencia de HK/HV caiga a Object y no pueda devolver Map<String, String>.
            HashOperations<String, String, String> hashes = redis.opsForHash();

            String actual = requerir(
                    redis.opsForValue().get(clave(settings, CURRENT_VERSION_KEY)),
                    CURRENT_VERSION_KEY);
            String masVieja = requerir(
                    redis.opsForValue().get(clave(settings, OLDEST_AVAILABLE_VERSION_KEY)),
                    OLDEST_AVAILABLE_VERSION_KEY);
            Map<String, String> metadata = hashes.entries(clave(settings, METADATA_KEY));
            String publicado = requerir(metadata.get("publishedAt"), METADATA_KEY + ".publishedAt");

            return new SyncState(
                    aNumero(actual, CURRENT_VERSION_KEY),
                    aNumero(masVieja, OLDEST_AVAILABLE_VERSION_KEY),
                    aInstante(publicado, METADATA_KEY + ".publishedAt"));
        });
    }

    @Override
    public CatalogChanges changes(long version) {
        CatedraRedisSettings settings = settingsSource.currentRedisSettings();
        StringRedisTemplate redis = plantilla(settings);
        String clave = clave(settings, CHANGES_KEY_PREFIX + version);

        String body = enRedis("los cambios de la versión " + version,
                () -> redis.opsForValue().get(clave));

        // Una versión que no existe salió de la ventana retenida: no es un error de red, es la
        // señal de discontinuidad que el caso de uso traduce en un snapshot (anexo §18.2).
        if (body == null) {
            return new CatalogChanges(version, List.of(), List.of(), List.of());
        }

        CatedraChangesResponse response = parse(body, CatedraChangesResponse.class, clave);
        CatedraChangesResponse.Changes changes = response.changes();

        return new CatalogChanges(
                version,
                cambios(changes == null ? null : changes.professionalCategories()),
                cambios(changes == null ? null : changes.professionals()),
                cambios(changes == null ? null : changes.weeklySchedules()));
    }

    @Override
    public List<ProfessionalCategory> professionalCategories(Collection<Long> ids) {
        return leerEntidades(ids, PROFESSIONAL_CATEGORIES_KEY,
                CatedraProfessionalCategoryResponse.class, this::categoria);
    }

    @Override
    public List<Professional> professionals(Collection<Long> ids) {
        return leerEntidades(ids, PROFESSIONALS_KEY,
                CatedraProfessionalResponse.class, this::profesional);
    }

    @Override
    public List<WeeklySchedule> weeklySchedules(Collection<Long> ids) {
        return leerEntidades(ids, WEEKLY_SCHEDULES_KEY,
                CatedraWeeklyScheduleResponse.class, this::horario);
    }

    /**
     * Lee entidades por ID en un solo {@code HMGET}, descartando las que no existen.
     *
     * <p>Un ID listado en los cambios puede corresponder a una entidad que ya no está: la cátedra
     * pudo darla de baja de verdad después de publicar la versión. No aparecer no es un fallo,
     * es una entidad que no hay que aplicar.
     */
    private <D, E> List<D> leerEntidades(
            Collection<Long> ids,
            String seccion,
            Class<E> tipoDto,
            java.util.function.Function<E, D> aDominio) {

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        CatedraRedisSettings settings = settingsSource.currentRedisSettings();
        StringRedisTemplate redis = plantilla(settings);
        String clave = clave(settings, seccion);
        List<String> campos = ids.stream().distinct().map(String::valueOf).toList();

        return enRedis("las entidades de " + seccion, () -> {
            HashOperations<String, String, String> hashes = redis.opsForHash();
            List<String> valores = hashes.multiGet(clave, campos);
            if (valores == null) {
                return List.of();
            }

            return valores.stream()
                    .filter(valor -> valor != null)
                    .map(valor -> aDominio.apply(parse(valor, tipoDto, clave)))
                    .toList();
        });
    }

    // ------------------------------------------------------------------ conexión

    /**
     * La conexión abierta, reabrirla solo si cambiaron los datos.
     *
     * <p>La comparación es por valor del registro completo, así que un cambio de contraseña
     * también la dispara. Al reemplazar se cierra la anterior para no dejar un cliente de
     * Lettuce sin uso; hay una ventana chica en la que un hilo que ya tenía la vieja puede
     * fallar, y ese fallo sale como {@code catedra-unavailable}, que es lo que el caso de uso
     * haría igual con una cátedra caída.
     */
    private StringRedisTemplate plantilla(CatedraRedisSettings settings) {
        CachedConnection hit = cache;
        if (hit != null && hit.settings().equals(settings)) {
            return hit.template();
        }

        synchronized (this) {
            hit = cache;
            if (hit != null && hit.settings().equals(settings)) {
                return hit.template();
            }

            CachedConnection nueva = abrir(settings);
            CachedConnection anterior = cache;
            cache = nueva;
            if (anterior != null) {
                anterior.factory().destroy();
            }
            return nueva.template();
        }
    }

    private static CachedConnection abrir(CatedraRedisSettings settings) {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(settings.host(), settings.port());
        config.setUsername(settings.username());
        config.setPassword(RedisPassword.of(settings.password()));

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        return new CachedConnection(settings, template, factory);
    }

    /** Conexión junto con la configuración con la que se abrió. */
    private record CachedConnection(
            CatedraRedisSettings settings,
            StringRedisTemplate template,
            LettuceConnectionFactory factory) {
    }

    // ------------------------------------------------------------------ claves

    /**
     * Clave completa a partir de la sección del contrato.
     *
     * <p>El prefijo sale de {@code redisReadNamespace}, que llega como {@code catedra:sync:*}:
     * se le quita el {@code :*} y se le pega la sección. Si no viniera informado, es un error
     * de la integración y se dice con ese mensaje en vez de fallar después con claves raras.
     */
    private static String clave(CatedraRedisSettings settings, String seccion) {
        String namespace = settings.readNamespace();
        if (namespace == null || namespace.isBlank()) {
            throw new CatedraUnavailableException(
                    "La integración no informa redisReadNamespace: no se sabe qué claves leer.");
        }

        String prefijo = namespace.endsWith(":*")
                ? namespace.substring(0, namespace.length() - 2)
                : namespace;
        return prefijo + ":" + seccion;
    }

    // ------------------------------------------------------------------ parseo

    private static String requerir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new CatedraUnavailableException(
                    "La cátedra no publica " + campo + " en Redis.");
        }
        return valor;
    }

    private static long aNumero(String valor, String campo) {
        try {
            return Long.parseLong(valor.trim());
        } catch (RuntimeException e) {
            throw new CatedraUnavailableException(
                    "La cátedra publicó un " + campo + " que no es un número.", e);
        }
    }

    private static java.time.Instant aInstante(String valor, String campo) {
        try {
            return java.time.Instant.parse(valor.trim());
        } catch (RuntimeException e) {
            throw new CatedraUnavailableException(
                    "La cátedra publicó un " + campo + " que no es un instante ISO-8601.", e);
        }
    }

    private static List<Long> cambios(List<Long> ids) {
        return ids == null ? List.of() : List.copyOf(ids);
    }

    /** JSON a DTO, con un mensaje que dice qué clave falló sin repetir el valor. */
    private <T> T parse(String body, Class<T> tipo, String clave) {
        try {
            return objectMapper.readValue(body, tipo);
        } catch (RuntimeException e) {
            throw new CatedraUnavailableException(
                    "La cátedra guardó algo que no se pudo entender en " + clave + ".", e);
        }
    }

    /**
     * Ejecuta una operación de Redis y la traduce a la excepción de aplicación.
     *
     * <p>Una {@link ApplicationException} que ya venía clasificada —por ejemplo credenciales
     * rechazadas al pedir la configuración— se propaga tal cual: reetiquetarla como
     * indisponible haría que el operador busque la red cuando el problema es la cuenta.
     */
    private <T> T enRedis(String operacion, Supplier<T> operacionRedis) {
        try {
            return operacionRedis.get();
        } catch (ApplicationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CatedraUnavailableException(
                    "No se pudieron leer " + operacion + " en Redis.", e);
        }
    }

    // ------------------------------------------------------------------ a dominio

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
