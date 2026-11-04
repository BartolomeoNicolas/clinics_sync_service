package com.bartolomeo.clinics.sync.shared.infrastructure.catedra;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Los topics de Kafka de la cátedra.
 *
 * <p><strong>El topic del catálogo lo publica la cátedra y lo escucha este servicio.</strong>
 * Su nombre lleva el {@code groupId} de la cuenta técnica, que es como la cátedra separa lo
 * de cada alumno.
 *
 * <p><strong>Kafka es notificación y no fuente de datos.</strong> El topic no lleva el
 * catálogo: lleva el aviso de que hay una versión nueva. Por eso perder un aviso no pierde
 * información, y por eso el consumidor no lee del topic lo que tiene que sincronizar.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "clinics.catedra.kafka")
public class CatedraKafkaProperties {

    /**
     * Grupo de la cuenta técnica.
     *
     * <p>Es el mismo {@code groupId} que aparece en el topic y en el mensaje que la cátedra
     * publica: es cómo se sabe de qué cuenta es el aviso.
     */
    @NotBlank(message = "falta el groupId de la cuenta tecnica: definí CATEDRA_GROUP_ID")
    private String groupId;

    /**
     * Topic donde la cátedra publica los avisos de versión nueva del catálogo.
     *
     * <p>Es el que consume este servicio.
     */
    @NotBlank(message = "falta el topic de catalogo de la cátedra: definí CATEDRA_CATALOG_TOPIC")
    private String catalogTopic;

    /**
     * Grupo de consumidores de este servicio.
     *
     * <p>Tantos servicios como grupos distintos: si dos servicios compartieran el grupo, se
     * repartirían los mensajes entre los dos y cada uno recibiría la mitad.
     */
    @NotBlank(message = "falta el grupo de consumidores: definí CATEDRA_CONSUMER_GROUP")
    private String consumerGroup;

    /**
     * Cuántos reintentos se dan ante un error recuperable.
     *
     * <p>Acotados a propósito: un error que se repite no se arregla insistiendo, y un bucle
     * sin fin deja la partición trabada para los mensajes que sí se podían aplicar.
     */
    private int retries = 3;

    /**
     * Cuánto se espera entre reintentos, en milisegundos.
     */
    private long retryIntervalMs = 1000L;
}
