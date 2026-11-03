package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.adapter;

import com.bartolomeo.clinics.sync.catalog.domain.model.PageResult;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalSearchCriteria;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogQueryPort;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.WeeklyScheduleEntity;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.mapper.CatalogReplicaMapper;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalCategoryRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.ProfessionalRepository;
import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository.WeeklyScheduleRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link CatalogQueryPort} sobre PostgreSQL.
 *
 * <p><strong>No tiene ningún camino hacia la cátedra, y esa es la garantía que hace
 * verificable el enunciado §4.1.</strong> Este adaptador solo conoce repositorios Spring Data.
 * Ni siquiera inyecta el token ni las propiedades de la cátedra, así que buscar con el servicio
 * central caído no es algo que se pueda probar: es algo que no puede pasar de otra forma. El
 * test lo verifica igual, pidiendo a un WireMock que falle si recibe tráfico.
 *
 * <p>Los filtros se combinan, no se encadenan: quien marca categoría <em>y</em> nombre
 * <em>y</em> un día recibe la intersección. Y el orden de cada resultado es parte de la
 * firma, no un detalle, porque sin orden estable paginar dos veces seguidas podría devolver
 * cosas distintas.
 *
 * <p>Todo corre en una transacción de solo lectura: no escribe nada y conviene decirlo, para
 * que nadie agregue acá una escritura sin darse cuenta de que rompe el compromiso.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryAdapter implements CatalogQueryPort {

    private final ProfessionalRepository professionals;
    private final ProfessionalCategoryRepository categories;
    private final WeeklyScheduleRepository schedules;

    @Override
    public PageResult<Professional> search(ProfessionalSearchCriteria criteria) {

        // El id entra como desempate: sin él, dos profesionales con el mismo nombre podrían
        // quedar en páginas distintas entre una consulta y la siguiente.
        Page<ProfessionalEntity> page = professionals.findAll(
                specification(criteria),
                PageRequest.of(criteria.page(), criteria.size(),
                        Sort.by(Sort.Direction.ASC, "lastName", "firstName", "id")));

        return new PageResult<>(
                page.getContent().stream().map(CatalogReplicaMapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public Optional<Professional> findById(long id) {
        return professionals.findById(id).map(CatalogReplicaMapper::toDomain);
    }

    @Override
    public List<ProfessionalCategory> findAllCategories() {
        return categories.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(CatalogReplicaMapper::toDomain)
                .toList();
    }

    @Override
    public List<WeeklySchedule> findWeeklySchedules(long professionalId) {
        return schedules.findByProfessionalId(professionalId).stream()
                .map(CatalogReplicaMapper::toDomain)
                .sorted(Comparator.comparing(WeeklySchedule::dayOfWeek)
                        .thenComparing(WeeklySchedule::startTime))
                .toList();
    }

    /** Une los filtros que vinieron; los que no vinieron no agregan condición. */
    private static Specification<ProfessionalEntity> specification(
            ProfessionalSearchCriteria criteria) {

        Specification<ProfessionalEntity> spec = (root, query, cb) -> cb.conjunction();

        if (criteria.categoryId() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("categoryId"), criteria.categoryId()));
        }

        if (criteria.name() != null) {
            spec = spec.and(namedLike(criteria.name()));
        }

        // El constructor del criterio ya garantizó que no viene en null: el parámetro ausente
        // en la petición quedó convertido en "solo habilitados".
        boolean soloHabilitados = Boolean.TRUE.equals(criteria.enabled());
        spec = spec.and((root, query, cb) -> soloHabilitados
                ? cb.isTrue(root.get("enabled"))
                : cb.isFalse(root.get("enabled")));

        if (criteria.availableOn() != null) {
            spec = spec.and(hasAgendaOn(criteria.availableOn()));
        }

        return spec;
    }

    /**
     * Coincide contra el nombre <em>o</em> el apellido, sin distinguir mayúsculas.
     *
     * <p><strong>Lo que el usuario escribe se escapa antes de meterlo en el patrón.</strong>
     * Un {@code %} o un {@code _} en la búsqueda significan comodín para SQL: sin escaparlos,
     * buscar "100%" devolvería todo lo que tenga un 0 seguido de cualquier cosa. Con el
     * escape puesto, el comodín es texto.
     *
     * <p><strong>Limitación conocida y elegida:</strong> no se doblan acentos. Buscar "gomez"
     * no encuentra "Gómez". Doblarlos requeriría la extensión {@code unaccent} de PostgreSQL
     * —una migración más— o normalizar al guardar, que cambiaría los datos que vienen de la
     * cátedra. No vale la pena para los nombres de este catálogo, y queda dicho en vez de
     * escondido.
     */
    private static Specification<ProfessionalEntity> namedLike(String name) {
        String pattern = patternFor(name);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                cb.like(cb.lower(root.get("lastName")), pattern, '\\'));
    }

    /** Escapa los comodines de {@code like} y pasa el texto a minúsculas, igual que la columna. */
    private static String patternFor(String name) {
        String escaped = name
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        // Locale.ROOT y no el default: con la locale turca "I".toLowerCase() da "ı", que ya no
        // coincide con lo que la base escribe en minúsculas.
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }

    /**
     * Existe alguna regla semanal habilitada ese día de la semana.
     *
     * <p>Se resuelve con un {@code exists} y no con un join, porque un join multiplicaría las
     * filas: un profesional con tres reglas ese día aparecería tres veces en la página y el
     * conteo de la paginación mentiría.
     *
     * <p>La regla tiene que estar habilitada, porque una agenda apagada no es disponibilidad.
     * Que el profesional además esté habilitado lo resuelve la condición de estado de arriba.
     */
    private static Specification<ProfessionalEntity> hasAgendaOn(LocalDate day) {
        return (root, query, cb) -> {
            Subquery<Long> exists = query.subquery(Long.class);
            Root<WeeklyScheduleEntity> schedule = exists.from(WeeklyScheduleEntity.class);

            exists.select(schedule.get("id"));
            exists.where(
                    cb.equal(schedule.get("professionalId"), root.get("id")),
                    cb.isTrue(schedule.get("enabled")),
                    cb.equal(schedule.get("dayOfWeek"), day.getDayOfWeek().name()));

            return cb.exists(exists);
        };
    }
}
