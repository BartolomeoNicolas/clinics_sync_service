package com.bartolomeo.clinics.sync.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Secretos que el servicio necesita del entorno para arrancar.
 *
 * <p>Viven acá, y no en {@code application.yml}, porque son valores sensibles y porque
 * queremos que su ausencia rompa el arranque <strong>temprano y con un mensaje que diga qué
 * falta</strong>.
 *
 * <p><strong>Por qué hace falta esto:</strong> un placeholder pelado como {@code ${DB_PASSWORD}}
 * <em>no</em> falla cuando no está definido: resuelve a vacío y el arranque sigue hasta que
 * algo intenta autenticarse, que es mucho más tarde y con un error que no menciona la variable
 * que faltaba. Con {@code @Validated} y {@code @NotBlank} el fallo ocurre al enlazar las
 * propiedades, y Spring nombra la propiedad exacta.
 *
 * @see CatedraProperties
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "clinics.local")
public class LocalProperties {

    /**
     * Contraseña de la base de datos local. Requerida.
     *
     * <p>Se inyecta en {@code spring.datasource.password} a través de
     * {@code ${clinics.local.db-password}}.
     */
    @NotBlank(message = "falta la contraseña de la base de datos: definí DB_PASSWORD en .env o en ~/.config/clinics/secrets/sync.yml")
    private String dbPassword;
}