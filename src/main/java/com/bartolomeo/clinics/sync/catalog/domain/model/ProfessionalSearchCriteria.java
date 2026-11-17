package com.bartolomeo.clinics.sync.catalog.domain.model;

import java.time.LocalDate;

/**
 * Qué pide quien busca profesionales en la copia local (enunciado §4.1).
 *
 * <p><strong>Ningún filtro consulta a la cátedra.</strong> Los cuatro son obligatorios por el
 * enunciado —categoría, nombre, estado habilitado y disponibilidad— y los cuatro se resuelven
 * contra las tablas locales. Ese es literalmente el requisito: <em>"buscar y filtrar
 * profesionales utilizando exclusivamente datos locales"</em>.
 *
 * <p><strong>{@code enabled} nunca llega en {@code null}.</strong> El parámetro HTTP sí admite
 * la ausencia, pero la ausencia no significa "todos": significa <em>solo habilitados</em>, que
 * es como los deshabilitados dejan de aparecer por defecto y a la vez se pueden pedir a
 * propósito con {@code enabled=false}. Normalizar en el constructor y no en el controller deja
 * esa regla escrita una sola vez, en el tipo que la representa, en vez de repartida en cada
 * llamada.
 *
 * <p><strong>{@code availableOn} es una fecha, no una hora.</strong> Responde a <em>"¿tiene
 * agenda habilitada ese día?"</em>, que es lo que se puede decidir con un horario semanal. Lo
 * que ocurre a una hora concreta —ocupaciones, holds— lo decide el servicio de turnos, que es
 * quien combina agenda con ocupaciones (enunciado §7). El catálogo no tiene esa información y
 * responder igual estaría inventando disponibilidad.
 *
 * <p>{@code page} y {@code size} llegan ya validados desde el límite de la aplicación: el
 * dominio no conoce HTTP y un índice negativo sería un error del llamador, no una regla de
 * negocio.
 *
 * @param categoryId  categoría exacta del profesional, o {@code null} para no filtrar
 * @param name        parte del nombre o del apellido, sin distinguir mayúsculas, o
 *                    {@code null}. En blanco se trata como ausente
 * @param enabled     {@code true} solo habilitados (también si viene ausente),
 *                    {@code false} solo deshabilitados
 * @param availableOn fecha en la que el profesional tiene que tener agenda habilitada, o
 *                    {@code null} para no filtrar por disponibilidad
 * @param page        índice de página desde cero
 * @param size        tamaño de página
 */
public record ProfessionalSearchCriteria(
        Long categoryId,
        String name,
        Boolean enabled,
        LocalDate availableOn,
        int page,
        int size) {

    public ProfessionalSearchCriteria {
        name = name == null || name.isBlank() ? null : name.trim();
        enabled = enabled == null || enabled;
    }
}
