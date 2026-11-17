package com.bartolomeo.clinics.sync.catalog.infrastructure.web.controller;

import com.bartolomeo.clinics.sync.catalog.application.service.CatalogQueryService;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalDetail;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalSearchCriteria;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.ProfessionalCategoryResponse;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.ProfessionalDetailResponse;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.ProfessionalSearchRequest;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.ProfessionalSearchResponse;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto.WeeklyScheduleResponse;
import com.bartolomeo.clinics.sync.catalog.infrastructure.web.mapper.CatalogWebMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de consulta del catálogo local (enunciado §4.1).
 *
 * <p><strong>Delgado de verdad, no solo corto.</strong> No atrapa excepciones —el
 * {@code @RestControllerAdvice} hace eso—, no traduce {@code Optional} —lo hace la fachada—,
 * no arma consultas —el caso de uso— y no sabe cómo se persiste nada. Lo único que hace es
 * pedir, traducir a DTO y devolver. Eso es lo que deja que la misma regla de negocio sirva
 * desde HTTP y desde un consumidor interno sin duplicarse.
 *
 * <p><strong>Ninguna de estas rutas habla con la cátedra.</strong> Están detrás del JWT como
 * todas las protegidas (enunciado §9), pero la información que sirven ya está en PostgreSQL:
 * buscar un profesional tiene que seguir funcionando con el servicio central caído, que es
 * lo que pide §4.1 al exigir usar <em>"exclusivamente datos locales"</em>.
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogQueryService catalog;

    /** Categorías para alimentar el filtro de búsqueda. */
    @GetMapping("/categories")
    public ResponseEntity<List<ProfessionalCategoryResponse>> categories() {
        List<ProfessionalCategoryResponse> items = catalog.listCategories().stream()
                .map(CatalogWebMapper::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    /**
     * Búsqueda local con los cuatro filtros obligatorios del enunciado §4.1 combinados.
     *
     * <p>Los parámetros viajan en un DTO validado y no sueltos: los límites quedan escritos
     * en un solo tipo y el cliente recibe el campo que falló, no un "inválido" genérico.
     */
    @GetMapping("/professionals")
    public ResponseEntity<ProfessionalSearchResponse> search(
            @Valid @ModelAttribute ProfessionalSearchRequest request) {

        ProfessionalSearchCriteria criteria = new ProfessionalSearchCriteria(
                request.categoryId(),
                request.name(),
                request.enabled(),
                request.availableOn(),
                request.page(),
                request.size());

        return ResponseEntity.ok(CatalogWebMapper.toSearchResponse(catalog.search(criteria)));
    }

    /** Ficha de un profesional con su agenda semanal completa. */
    @GetMapping("/professionals/{id}")
    public ResponseEntity<ProfessionalDetailResponse> detail(@PathVariable long id) {
        ProfessionalDetail detail = catalog.getProfessionalById(id);
        return ResponseEntity.ok(CatalogWebMapper.toDetailResponse(detail));
    }

    /**
     * Solo la agenda de un profesional: es la que consume el servicio de turnos para armar la
     * disponibilidad (enunciado §7), y por eso tiene su propio camino en vez de tener que
     * parsear el detalle completo.
     */
    @GetMapping("/professionals/{id}/weekly-schedules")
    public ResponseEntity<List<WeeklyScheduleResponse>> weeklySchedules(@PathVariable long id) {
        List<WeeklyScheduleResponse> items = catalog.getWeeklySchedules(id).stream()
                .map(CatalogWebMapper::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }
}
