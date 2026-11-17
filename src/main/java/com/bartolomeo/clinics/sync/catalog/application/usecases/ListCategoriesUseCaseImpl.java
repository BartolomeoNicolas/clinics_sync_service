package com.bartolomeo.clinics.sync.catalog.application.usecases;

import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalCategory;
import com.bartolomeo.clinics.sync.catalog.domain.ports.in.ListCategoriesUseCase;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogQueryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link ListCategoriesUseCase}.
 */
@Component
@RequiredArgsConstructor
public class ListCategoriesUseCaseImpl implements ListCategoriesUseCase {

    private final CatalogQueryPort repository;

    @Override
    public List<ProfessionalCategory> listCategories() {
        return repository.findAllCategories();
    }
}
