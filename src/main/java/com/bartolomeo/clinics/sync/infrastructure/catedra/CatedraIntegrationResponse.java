package com.bartolomeo.clinics.sync.infrastructure.catedra;

import com.bartolomeo.clinics.sync.sync.domain.model.ProvisioningStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Objeto {@code integration} de la cátedra, tal cual lo define el anexo §5.1 a §5.3.
 *
 * <p>Los nombres de campo son los del contrato, en snake_case o camelCase según lo define el
 * anexo, y no se reinterpretan: si la cátedra cambia un nombre, tiene que romper acá y no
 * silenciosamente en otro lado.
 *
 * <p><strong>Tolerante a campos desconocidos:</strong> el servicio de la cátedra es de otro
 * equipo y puede sumar campos; un campo nuevo no puede tumbar la sincronización entera.
 *
 * <p>{@code provisioningStatus} se lleva como {@link String} y no como {@link ProvisioningStatus}
 * a propósito: si la cátedra agregara un estado que no conocemos, un enum rompería el
 * mapeo entero con un error críptico, mientras que la conversión manual en el adaptador produce
 * un mensaje claro.
 *
 * @see CatedraIntegrationAuthAdapter
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CatedraIntegrationResponse(
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
        String provisioningStatus) {

    /** Sin la contraseña de Redis, por la misma razón que en el modelo de dominio. */
    @Override
    public String toString() {
        return "CatedraIntegrationResponse[groupId=%s, redis=%s:%d, provisioningStatus=%s, "
                .formatted(groupId, redisHost, redisPort, provisioningStatus)
                + "redisPassword=***, redisUsername=***]";
    }
}
