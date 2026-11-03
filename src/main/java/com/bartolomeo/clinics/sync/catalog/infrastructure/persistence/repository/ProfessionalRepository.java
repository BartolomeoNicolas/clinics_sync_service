package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository;

import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code professional}.
 *
 * <p>Además del reemplazo por lote de la réplica, la búsqueda obligatoria por categoría y
 * estado (SYNC-M1-06) se resuelve acá con filtros, nunca contra la cátedra.
 *
 * @see ProfessionalCategoryRepository
 */
public interface ProfessionalRepository extends JpaRepository<ProfessionalEntity, Long> {
}
