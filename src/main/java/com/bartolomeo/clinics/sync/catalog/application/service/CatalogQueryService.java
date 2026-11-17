package com.bartolomeo.clinics.sync.catalog.application.service;

import com.bartolomeo.clinics.sync.catalog.domain.model.PageResult;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalDetail;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalSearchCriteria;
import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.domain.ports.in.GetProfessionalUseCase;
import com.bartolomeo.clinics.sync.catalog.domain.ports.in.GetWeeklySchedulesUseCase;
import com.bartolomeo.clinics.sync.catalog.domain.ports.in.ListCategoriesUseCase;
import com.bartolomeo.clinics.sync.catalog.domain.ports.in.SearchProfessionalsUseCase;
import com.bartolomeo.clinics.sync.shared.application.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fachada de lectura del catálogo: lo que inyectan los controllers.
 *
 * <p><strong>Hace exactamente dos cosas, y ninguna es traducir a HTTP.</strong> Delega en los
 * casos de uso —el controller nunca toca un port directamente— y convierte el
 * {@code Optional} vacío en {@link NotFoundException}. Traducir a HTTP le corresponde a
 * {@code GlobalExceptionHandler}; acá solo se decide que ese id no existe.
 *
 * <p>La fachada no toca ningún puerto {@code out}: si lo hiciera, habría dos caminos hacia la
 * persistencia y la regla de que pasar por un caso de uso sería decorativa.
 *
 * <p>También evita repetir el chequeo de existencia. Pedir la agenda de un profesional que no
 * está es un 404 y no una lista vacía, y la fachada lo resuelve consultando primero si el
 * profesional existe, en vez de que cada caso de uso tenga que saberlo.
 */
@Service
@RequiredArgsConstructor
public class CatalogQueryService {

    private final SearchProfessionalsUseCase searchProfessionals;
    private final GetProfessionalUseCase getProfessional;
    private final ListCategoriesUseCase listCategories;
    private final GetWeeklySchedulesUseCase getWeeklySchedules;

    /**
     * Busca profesionales con los filtros combinados.
     *
     * <p>Nunca lanza por "sin resultados": una página vacía es un resultado, no un error.
     */
    public PageResult<Professional> search(ProfessionalSearchCriteria criteria) {
        return searchProfessionals.search(criteria);
    }

    /**
     * @return la ficha y la agenda del profesional
     * @throws NotFoundException si no está en la copia local
     */
    public ProfessionalDetail getProfessionalById(long id) {
        return getProfessional.getProfessionalById(id)
                .orElseThrow(() -> new NotFoundException(
                        "No existe el profesional " + id + " en el catálogo local."));
    }

    public List<ProfessionalCategory> listCategories() {
        return listCategories.listCategories();
    }

    /**
     * Solo la agenda, para el endpoint que la pide por separado.
     *
     * @return la agenda semanal, ordenada
     * @throws NotFoundException si el profesional no está en la copia local. Una agenda vacía
     *                           de un profesional que sí existe no es un 404
     */
    public List<WeeklySchedule> getWeeklySchedules(long professionalId) {
        getProfessionalById(professionalId);
        return getWeeklySchedules.getWeeklySchedules(professionalId);
    }
}
