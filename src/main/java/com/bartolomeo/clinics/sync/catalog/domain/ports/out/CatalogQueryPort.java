package com.bartolomeo.clinics.sync.catalog.domain.ports.out;

import com.bartolomeo.clinics.sync.catalog.domain.model.PageResult;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalSearchCriteria;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import java.util.List;
import java.util.Optional;

/**
 * Lecturas del catálogo sobre la copia local.
 *
 * <p><strong>Es el otro lado de {@link CatalogReplicaPort}.</strong> Ese escribe la réplica y
 * este la lee; los dos hablan con PostgreSQL y ningún caso de uso sabe de cuál de los dos se
 * trata. Separarlos deja el camino de escritura —que corre dentro de la transacción de
 * sincronización— libre de métodos de consulta que nadie debería usar ahí.
 *
 * <p>Los cuatro métodos son consultas puros: ninguno tiene efectos, ninguno habla con la
 * cátedra y ninguno puede bloquear una sincronización. Es la mitad del camino que hace
 * posible que buscar un profesional funcione con la cátedra caída.
 *
 * <p>El orden de cada resultado es parte de la firma del método, no un detalle del adaptador:
 * sin él, paginar dos veces seguidas podría devolver cosas distintas.
 */
public interface CatalogQueryPort {

    /**
     * Busca profesionales con los filtros combinados.
     *
     * @param criteria filtros y página pedida. Nunca {@code null}
     * @return la página pedida, ordenada por apellido y después por nombre. Nunca {@code
     *         null}; la página puede venir vacía
     */
    PageResult<Professional> search(ProfessionalSearchCriteria criteria);

    /**
     * @param id identificador del profesional
     * @return el profesional si está en la copia local; vacío si no
     */
    Optional<Professional> findById(long id);

    /**
     * @return todas las categorías ordenadas por nombre. Nunca {@code null}; vacía si todavía
     *         no hay catálogo
     */
    List<ProfessionalCategory> findAllCategories();

    /**
     * @param professionalId identificador del profesional
     * @return sus reglas semanales de lunes a domingo y por hora de inicio dentro de cada
     *         día, incluidas las deshabilitadas. Nunca {@code null}
     */
    List<WeeklySchedule> findWeeklySchedules(long professionalId);
}
