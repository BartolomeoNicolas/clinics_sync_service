package com.bartolomeo.clinics.sync.shared.infrastructure.catedra;

/**
 * Proveedor de los datos de conexión a Redis que entrega la cátedra (anexo §14.1).
 *
 * <p><strong>Interfaz de infraestructura, no un puerto de dominio.</strong> Está en
 * {@code shared} para que un adapter de {@code catalog} pueda conectar a Redis sin importar
 * nada del dominio de {@code sync}, que es donde vive la configuración completa de la
 * integración. Es la misma razón por la que {@link CatedraTokenSource} está acá: lo que los
 * dos contexts necesitan en común se resuelve en {@code shared} y no con una flecha entre
 * contexts.
 *
 * <p>Los valores vienen de la configuración que devuelve la cátedra, no de las properties
 * locales: el anexo §5 es tajante en que la integración técnica se obtiene del JWT, y una
 * confusión acá mandaría las credenciales al Redis equivocado.
 *
 * <p>Quien implementa esto es el adaptador de autenticación, que es el que ya tiene la sesión
 * y la configuración a mano; hace una proyección y nada más.
 *
 * @see com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogChangesPort
 */
public interface CatedraRedisSettingsSource {

    /**
     * Datos de conexión vigentes, refrescándose solos si hace falta.
     *
     * <p>Puede disparar un login contra la cátedra si no hay sesión vigente; quien llama no
     * tiene por qué saberlo.
     *
     * <p><strong>La contraseña que devuelve es un secreto.</strong> Se usa para abrir la
     * conexión y se descarta: no debe imprimirse ni guardarse en un campo de larga vida.
     *
     * @return la conexión vigente. Nunca {@code null}.
     * @throws com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException si
     *         la cátedra rechaza las credenciales o no se puede entender su respuesta
     */
    CatedraRedisSettings currentRedisSettings();
}
