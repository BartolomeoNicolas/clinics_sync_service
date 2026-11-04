package com.bartolomeo.clinics.sync.catalog.domain.ports.in;

import com.bartolomeo.clinics.sync.catalog.domain.model.PageResult;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalSearchCriteria;

/**
 * Búsqueda de profesionales sobre la copia local del catálogo (enunciado §4.1).
 *
 * <p><strong>Es una consulta, no una integración.</strong> Este caso de uso no tiene ningún
 * puerto hacia la cátedra: no puede tenerlo. El enunciado exige que la búsqueda use
 * <em>"exclusivamente datos locales"</em> y que el servicio sea <em>"la unica fuente local
 * vigente para consultar profesionales"</em>. Si esta operación llegara a tocar la cátedra,
 * dejaría de funcionar cuando la cátedra esté caída, que es exactamente lo que el enunciado
 * prohíbe para una búsqueda.
 *
 * <p>Los cuatro filtros obligatorios —categoría, nombre, estado habilitado y disponibilidad—
 * viajan juntos en {@link ProfessionalSearchCriteria} y se aplican combinados, no en cadena:
 * pedir categoría <em>y</em> nombre <em>y</em> un día devuelve la intersección, que es lo que
 * un usuario espera al marcar varios filtros a la vez.
 *
 * <p>Corresponde a las evidencias 4 y 5 del §11: <em>"busquedas resueltas sobre datos
 * locales"</em> y <em>"funcionamiento de los filtros obligatorios"</em>.
 *
 * @see ProfessionalSearchCriteria
 */
public interface SearchProfessionalsUseCase {

    /**
     * Devuelve la página que se pidió, con los filtros aplicados a la vez.
     *
     * <p>El resultado nunca es {@code null}: una búsqueda sin coincidencias es una página
     * vacía con {@code totalElements} en cero, porque no encontrar nada es un resultado y no
     * un error.
     *
     * @param criteria los filtros y la página pedida. Nunca {@code null}
     * @return la página pedida. Nunca {@code null}
     */
    PageResult<Professional> search(ProfessionalSearchCriteria criteria);
}
