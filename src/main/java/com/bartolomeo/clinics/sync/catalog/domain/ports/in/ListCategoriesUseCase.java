package com.bartolomeo.clinics.sync.catalog.domain.ports.in;

import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import java.util.List;

/**
 * Listado de categorías, para alimentar el filtro de búsqueda desde la interfaz.
 *
 * <p>Devuelve también las deshabilitadas a propósito: quien arma el filtro tiene que poder
 * mostrarlas —o saber que existen y las excluye—, y esa decisión no le corresponde al
 * catálogo. Además una categoría sin profesionales habilitados deja de ser útil para filtrar,
 * y eso se ve desde el listado de profesionales, no acá.
 */
public interface ListCategoriesUseCase {

    /**
     * Todas las categorías de la copia local, ordenadas por nombre.
     *
     * @return la lista completa. Nunca {@code null}; vacía si todavía no hay catálogo
     */
    List<ProfessionalCategory> listCategories();
}
