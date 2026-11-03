package com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.repository;

import com.bartolomeo.clinics.sync.catalog.infrastructure.persistence.entity.ProfessionalCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code professional_category}.
 *
 * <p>{@code deleteAllInBatch()} y no {@code deleteAll()}: el reemplazo de la réplica borra
 * miles de filas y hacerlo cargándolas una por una para después borrarlas es un paseo
 * innecesario por la memoria. La variante por lote emite un único {@code delete} y deja que
 * haga el trabajo a la base.
 */
public interface ProfessionalCategoryRepository
        extends JpaRepository<ProfessionalCategoryEntity, Long> {
}
