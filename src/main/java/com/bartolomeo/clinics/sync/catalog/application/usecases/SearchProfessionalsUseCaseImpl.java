package com.bartolomeo.clinics.sync.catalog.application.usecases;

import com.bartolomeo.clinics.sync.catalog.domain.model.PageResult;
import com.bartolomeo.clinics.sync.catalog.domain.model.Professional;
import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalSearchCriteria;
import com.bartolomeo.clinics.sync.catalog.domain.ports.in.SearchProfessionalsUseCase;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link SearchProfessionalsUseCase}.
 *
 * <p>Delega entero: la regla de negocio de este caso de uso es <em>dónde</em> se busca —sobre
 * la copia local y en ningún otro lado—, y esa garantía está en el tipo, porque este caso de
 * uso solo inyecta {@link CatalogQueryPort} y no tiene forma de alcanzar a la cátedra ni un
 * adaptador REST que se le acerque. Que el método no haga nada más no es un vacío: es la
 * evidencia de que buscar es una consulta.
 */
@Component
@RequiredArgsConstructor
public class SearchProfessionalsUseCaseImpl implements SearchProfessionalsUseCase {

    private final CatalogQueryPort repository;

    @Override
    public PageResult<Professional> search(ProfessionalSearchCriteria criteria) {
        return repository.search(criteria);
    }
}
