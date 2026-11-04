package com.bartolomeo.clinics.sync.sync.domain.model;

/**
 * Configuración vigente de la integración técnica con la cátedra.
 *
 * <p>Es el objeto {@code integration} que devuelven {@code POST /api/authenticate} y
 * {@code GET /api/student/integration} (anexo §5.1 a §5.3), con los campos tal cual los
 * define el contrato: nombres, puertos y namespaces no se reinterpretan.
 *
 * <p><strong>De dónde sale el {@code groupId}:</strong> de esta respuesta, que está atada al
 * JWT técnico con el que se pidió. El anexo §6 es tajante: <em>"la integración técnica siempre
 * se obtiene del JWT; ninguna operación acepta {@code groupId} para elegir otra integración"</em>.
 * Por eso el groupId de acá no viene de la configuración local ni de ningún body de una
 * petición: es la cátedra la que dice con qué cuenta estamos trabajando, y una confusión ahí
 * mandaría datos al namespace de otro grupo.
 *
 * <p>El estado <a href="https://tools.ietf.org/html/rfc7519#section-4.1.3">{@code exp}</a> del
 * token con el que se obtuvo no viaja acá: lo lleva el adaptador, que es quien sabe si el
 * token sigue vigente.
 *
 * <p>Enmascara la contraseña de Redis en {@link #toString()}: los records imprimen todos sus
 * campos y esta contraseña no tiene por qué terminar en un log.
 */
public record IntegrationConfig(
        String groupId,
        String redisHost,
        int redisPort,
        String redisUsername,
        String redisPassword,
        String redisReadNamespace,
        String redisWriteNamespace,
        String kafkaBootstrapServers,
        String kafkaConsumerGroupId,
        String kafkaCatalogTopic,
        String kafkaAppointmentActionsTopic,
        String kafkaAppointmentPhoneTopic,
        ProvisioningStatus provisioningStatus) {

    /**
     * Igual que el generado, pero con la contraseña de Redis enmascarada.
     *
     * <p>Un record imprime todos sus componentes; si alguien loguea esta configuración —en un
     * {@code toString} de otra clase, en un debugger, en un error—, el secreto no sale.
     */
    @Override
    public String toString() {
        return "IntegrationConfig[groupId=%s, redis=%s:%d, redisReadNamespace=%s, "
                .formatted(groupId, redisHost, redisPort, redisReadNamespace)
                + "redisWriteNamespace=%s, kafkaBootstrapServers=%s, provisioningStatus=%s, "
                .formatted(redisWriteNamespace, kafkaBootstrapServers, provisioningStatus)
                + "redisPassword=***, redisUsername=***]";
    }
}
