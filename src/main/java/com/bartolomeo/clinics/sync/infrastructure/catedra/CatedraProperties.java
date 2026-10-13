package com.bartolomeo.clinics.sync.infrastructure.catedra;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del cliente de la cátedra, tipada.
 *
 * <p>Reemplaza los {@code ${...}} sueltos de {@code application.yml} por un objeto con getters.
 * Ningún valor secreto se define acá: se inyecta desde el entorno en el arranque.
 *
 * <p><strong>Por qué los secretos de la cátedra no son obligatorios al arrancar:</strong> la
 * cuenta técnica se registra una sola vez y es la cátedra la que entrega la contraseña de Redis
 * y el resto de la configuración, en la respuesta del registro. Antes de ese momento
 * {@code technicalUser} y {@code technicalPassword} están vacíos y el servicio igual tiene que
 * poder levantar. Lo que sí es obligatorio —la contraseña de la base local— se resuelve con
 * placeholders sin valor por defecto en {@code application.yml}, y la app falla nombrando la
 * variable que falta.
 *
 * <p>Valida el formato de lo que sí está informado: un {@code groupId} con caracteres raros o
 * un puerto fuera de rango se detectan al arrancar, no a mitad de una sincronización.
 *
 * @see ProvisioningStatus
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "clinics.catedra")
public class CatedraProperties {

    /** Base del REST de la cátedra, por ejemplo {@code http://172.17.201.1:8090}. */
    @NotBlank
    private String baseUrl;

    /**
     * Identificador de la cuenta técnica. Es <strong>inmutable</strong>: queda congelado en los
     * topics de Kafka y en los namespaces de Redis. Antes de registrar la cuenta hay que
     * confirmarlo con el profesor.
     */
    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,63}$", message = "groupId inválido: minúsculas, dígitos y guiones")
    private String groupId;

    /** Usuario técnico de la cátedra. Llega con el registro de la cuenta. */
    private String technicalUser;

    /** Contraseña del usuario técnico. Llega con el registro de la cuenta. */
    private String technicalPassword;

    /** Host del Redis de la cátedra. */
    @NotBlank
    private String redisHost;

    @NotNull
    @Min(1)
    @Max(65535)
    private int redisPort;

    /** Usuario del Redis de la cátedra. Llega con el registro de la cuenta. */
    private String redisUsername;

    /** Contraseña del Redis de la cátedra. Solo se entrega si el estado es PROVISIONED. */
    private String redisPassword;

    /** Estado de aprovisionamiento. Arranca en {@link ProvisioningStatus#PENDING}. */
    @NotNull
    private ProvisioningStatus provisioningStatus = ProvisioningStatus.PENDING;

    /**
     * Indica si ya se puede llamar a la cátedra.
     *
     * <p>Mientras sea {@code false} el cliente de cátedra debe fallar con un mensaje claro,
     * en vez de intentar autenticarse con credenciales vacías.
     */
    public boolean isProvisioned() {
        return provisioningStatus == ProvisioningStatus.PROVISIONED
                && technicalUser != null && !technicalUser.isBlank()
                && technicalPassword != null && !technicalPassword.isBlank();
    }
}