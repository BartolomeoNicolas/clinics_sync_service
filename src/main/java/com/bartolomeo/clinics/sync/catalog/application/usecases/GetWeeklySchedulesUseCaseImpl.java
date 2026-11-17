package com.bartolomeo.clinics.sync.catalog.application.usecases;

import com.bartolomeo.clinics.sync.catalog.domain.model.WeeklySchedule;
import com.bartolomeo.clinics.sync.catalog.domain.ports.in.GetWeeklySchedulesUseCase;
import com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogQueryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link GetWeeklySchedulesUseCase}.
 *
 * <p>Devuelve la agenda completa —también las reglas deshabilitadas— porque turnos es quien
 * tiene que descartarlas al armar los slots (enunciado §7). Filtrar acá le escondería el caso
 * que allá tiene que rechazar.
 */
@Component
@RequiredArgsConstructor
public class GetWeeklySchedulesUseCaseImpl implements GetWeeklySchedulesUseCase {

    private final CatalogQueryPort repository;

    @Override
    public List<WeeklySchedule> getWeeklySchedules(long professionalId) {
        return repository.findWeeklySchedules(professionalId);
    }
}
