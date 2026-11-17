package com.bartolomeo.clinics.sync.catalog.application.usecases;

import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalDetail;
import com.bartolomeo.clinics.sync.catalog.domain.ports.in.GetProfessionalUseCase;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogQueryPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link GetProfessionalUseCase}.
 *
 * <p>Las dos consultas van acá y no en la fachada: el caso de uso es el dueño de la pregunta
 * <em>"¿existe y qué trae?"</em>, y la fachada no debería saber que contestarla son dos
 * lecturas. Devuelve {@code Optional} y no lanza, porque que un id no exista todavía no es un
 * error HTTP: lo decide la fachada.
 */
@Component
@RequiredArgsConstructor
public class GetProfessionalUseCaseImpl implements GetProfessionalUseCase {

    private final CatalogQueryPort repository;

    @Override
    public Optional<ProfessionalDetail> getProfessionalById(long id) {
        return repository.findById(id)
                .map(professional -> new ProfessionalDetail(
                        professional, repository.findWeeklySchedules(id)));
    }
}
