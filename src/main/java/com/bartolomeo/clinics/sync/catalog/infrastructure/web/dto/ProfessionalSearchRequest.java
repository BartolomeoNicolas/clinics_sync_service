package com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Los parámetros de búsqueda de {@code GET /api/catalog/professionals}.
 *
 * <p><strong>Es un DTO igual que los de respuesta, y no parámetros sueltos en el
 * controller.</strong> Los cuatro filtros obligatorios del enunciado §4.1 conviven en un solo
 * tipo con su validación al lado: se ve de un vistazo qué admite la búsqueda, y si mañana se
 * agrega un filtro se agrega acá y no se le suma otro argumento al método del controller.
 *
 * <p>Además la validación produce errores <strong>por campo</strong> en el
 * {@code problem+json} —{@code "fieldErrors": [{"field": "size", ...}]},— que es mucho más
 * útil para el cliente que un {@code "Parámetros inválidos."} sin decir cuál.
 *
 * <p>Los campos son anulables a propósito: la ausencia es distinta de un valor, y esa
 * distinción es la que permite que {@code enabled} ausente signifique "solo habilitados" y no
 * "todos". Los valores por defecto se aplican en el propio registro, que es el único lugar
 * donde se sabe qué es "no lo pedí".
 *
 * @param page         desde cero. Ausente: {@code 0}
 * @param size         hasta 100. Ausente: {@code 20}
 * @param categoryId   categoría exacta. Ausente: sin filtro
 * @param name         parte del nombre o del apellido. Ausente: sin filtro
 * @param enabled      {@code true} solo habilitados, {@code false} solo deshabilitados.
 *                     Ausente: solo habilitados
 * @param availableOn  fecha en la que tiene que tener agenda habilitada. Ausente: sin filtro
 */
public record ProfessionalSearchRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        Long categoryId,
        @Size(max = 100) String name,
        Boolean enabled,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableOn) {

    /** Tamaño de página cuando no se pidió. Suficiente para una pantalla y acotado a 100. */
    private static final int TAMANIO_POR_DEFECTO = 20;

    public ProfessionalSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? TAMANIO_POR_DEFECTO : size;
    }
}
