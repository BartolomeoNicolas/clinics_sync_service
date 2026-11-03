package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository;

import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Acceso a {@code professional}.
 *
 * <p>Además del reemplazo por lote de la réplica, la búsqueda obligatoria por categoría,
 * nombre, estado y disponibilidad (SYNC-M1-06) se resuelve acá con filtros, nunca contra la
 * cátedra.
 *
 * <p>Se agrega {@link JpaSpecificationExecutor} porque los cuatro filtros son
 * <strong>opcionales y combinables</strong>: una query derivada obligaría a escribir todas las
 * combinaciones posibles a mano, y una consulta con parámetros anulables en JPQL obligaría a
 * escribir {@code (:p IS NULL OR ...)}, que Hibernate resuelve mal en cuanto hay tipos que no
 * admite null. Las especificaciones construyen la condición que corresponde para los filtros
 * que vinieron, y dejan el resto afuera.
 *
 * @see ProfessionalCategoryRepository
 */
public interface ProfessionalRepository
        extends JpaRepository<ProfessionalEntity, Long>, JpaSpecificationExecutor<ProfessionalEntity> {
}
