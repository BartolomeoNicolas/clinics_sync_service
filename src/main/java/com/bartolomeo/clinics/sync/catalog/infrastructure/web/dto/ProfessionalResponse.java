package com.bartolomeo.clinics.sync.catalog.infrastructure.web.dto;

import java.time.Instant;

/**
 * Un profesional tal como se lo muestra a quien lo busca.
 *
 * <p><strong>Es contrato, no una vista interna.</strong> El servicio de turnos lo deserializa
 * en su test de contrato (BOOK-M1-01) y la app KMP lo pinta; por eso los campos son los que
 * la entidad tiene y no se les quita nada para "limpiar": sacar {@code updatedAt} obligaría a
 * esos dos clientes a reescribirse si algún día se quiere saber cuándo se actualizó un
 * profesional. Lo que no existe en la entidad tampoco se inventa acá.
 *
 * @param id         identificador
 * @param categoryId categoría a la que pertenece
 * @param firstName  nombre
 * @param lastName   apellido
 * @param enabled    si está habilitado. Los deshabilitados no aparecen en una búsqueda sin
 *                   filtro explícito
 * @param createdAt  cuándo se registró en la cátedra
 * @param updatedAt  cuándo lo actualizó la cátedra
 */
public record ProfessionalResponse(
        long id,
        long categoryId,
        String firstName,
        String lastName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
