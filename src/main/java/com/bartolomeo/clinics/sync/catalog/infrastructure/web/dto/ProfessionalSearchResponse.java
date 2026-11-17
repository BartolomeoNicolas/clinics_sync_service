package com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto;

import java.util.List;

/**
 * Una página de resultados de búsqueda.
 *
 * <p><strong>Los datos de paginación van explícitos y no en el formato interno de
 * Spring.</strong> {@code org.springframework.data.domain.Page} serializa diez campos, varios
 * de ellos sobre cómo Spring construyó la página, y ninguno de ellos es contrato de este
 * servicio. Acá hay exactamente lo que una pantalla necesita para paginar: qué hay, dónde
 * está, cuánto hay y cuántas páginas da. Si mañana se cambia de fuente de datos, esta
 * respuesta no se mueve.
 *
 * @param items         los profesionales de esta página
 * @param page          página actual, desde cero
 * @param size          cuántos entran en una página
 * @param totalElements cuántos hay en total
 * @param totalPages    cuántas páginas da todo el conjunto
 */
public record ProfessionalSearchResponse(
        List<ProfessionalResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
