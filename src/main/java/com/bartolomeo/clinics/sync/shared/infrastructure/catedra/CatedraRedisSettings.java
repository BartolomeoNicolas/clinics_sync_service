package com.bartolomeo.clinics.sync.shared.infrastructure.catedra;

/**
 * Datos de conexión a Redis de la cátedra, tal cual los entrega el objeto {@code integration}
 * (anexo §5.3 y §14.1).
 *
 * <p><strong>Es una proyección, no una segunda fuente de verdad.</strong> El objeto completo es
 * {@code IntegrationConfig}, que vive en el dominio del contexto {@code sync}. Éste registro
 * existe en {@code shared} para que los adapters de otros contexts —el lector Redis de
 * {@code catalog}, que es el caso que lo motiva— no tengan que importar los tipos del dominio
 * de {@code sync}: dependerían de {@code shared}, que es la dirección correcta. Quien proyecta
 * es el adaptador de autenticación, que es el que tiene la configuración a mano.
 *
 * <p>Los nombres de campo son los del contrato y no se reinterpretan. {@code readNamespace} y
 * {@code writeNamespace} llegan como patrones glob del anexo §5.3 —por ejemplo
 * {@code catedra:sync:*} y {@code alumnos:proyecto-juan-perez:*}—, no como prefijos: quien los
 * usa decide cómo derivar las claves concretas.
 *
 * <p><strong>Enmascara la contraseña en {@link #toString()}</strong>, por la misma razón que
 * {@code IntegrationConfig}: un record imprime todos sus campos, y este secreto no tiene por
 * qué terminar en un log.
 */
public record CatedraRedisSettings(
        String host,
        int port,
        String username,
        String password,
        String readNamespace,
        String writeNamespace) {

    /** Sin usuario ni contraseña, que son los secretos del ACL. */
    @Override
    public String toString() {
        return "CatedraRedisSettings[host=%s, port=%d, readNamespace=%s, writeNamespace=%s, "
                .formatted(host, port, readNamespace, writeNamespace)
                + "username=***, password=***]";
    }
}
