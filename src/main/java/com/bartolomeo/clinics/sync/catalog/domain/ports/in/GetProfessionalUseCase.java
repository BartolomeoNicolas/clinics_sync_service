package com.bartolomeo.clinics.sync.catalog.domain.ports.in;

import com.bartolomeo.clinics.sync.catalog.domain.model.ProfessionalDetail;
import java.util.Optional;

/**
 * Detalle de un profesional con su agenda, sobre la copia local.
 *
 * <p>Igual que la búsqueda, no tiene puertos hacia la cátedra: es una lectura de lo que ya
 * está sincronizado.
 *
 * <p><strong>Devuelve ficha y agenda juntas a propósito.</strong> Pedirlas por separado
 * obligaría a comprobar dos veces que el profesional existe —una para contestar 404 y otra
 * para no devolver la agenda de nadie—. Juntas, se pregunta una vez.
 *
 * <p>El {@code Optional} se resuelve en la fachada y no en el controller: quien decide que un
 * id inexistente es un 404 es la capa de aplicación, que es donde está la regla de que ese
 * recurso debería existir. El controller solo traduce la excepción a HTTP.
 *
 * @see GetWeeklySchedulesUseCase
 */
public interface GetProfessionalUseCase {

    /**
     * @param id identificador del profesional
     * @return su ficha y su agenda si existe en la copia local; vacío si no está
     */
    Optional<ProfessionalDetail> getProfessionalById(long id);
}
