package com.bartolomeo.clinics.sync.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Falla el arranque si falta un secreto obligatorio, antes de crear ningún bean.
 *
 * <p><strong>Por qué hace falta esto y no alcanza con {@code @Validated}:</strong>
 * {@code @ConfigurationProperties} + {@code @NotBlank} valida cuando se instancia el bean. Pero
 * Hibernate crea su {@code EntityManagerFactory} durante la actualización del contexto, y eso
 * ocurre <em>antes</em> que la instanciación de los singletons. El resultado es que la base
 * intenta conectarse con la contraseña vacía y el error dice
 * {@code password authentication failed} — que no menciona ninguna variable.
 *
 * <p>Verificado el 13/10: con el secreto ausente, la app llegaba hasta el driver de Postgres
 * y moría allí. Con este inicializador muere antes y diciendo qué falta.
 *
 * <p>Los inicializadores de contexto corren después de cargar las propiedades y antes de crear
 * beans, que es exactamente lo que hace falta. No hay que registrar nada en
 * {@code spring.factories}: se agrega en {@code main}.
 *
 * @see LocalProperties
 */
public class RequiredSecretsInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * Secretos obligatorios para arrancar, y cómo se documentan aldeveloper.
     *
     * <p>La clave es la propiedad; el valor es lo que el mensaje le muestra a quien lee el
     * error, para que sepa dónde definirla.
     */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("clinics.local.db-password", "DB_PASSWORD");
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {

        ConfigurableEnvironment env = context.getEnvironment();

        Map<String, String> missing = new LinkedHashMap<>();
        REQUIRED.forEach((property, variable) -> {
            if (!StringUtils.hasText(env.getProperty(property))) {
                missing.put(property, variable);
            }
        });

        if (missing.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder()
                .append(System.lineSeparator())
                .append("Faltan secretos requeridos para arrancar:")
                .append(System.lineSeparator());

        missing.forEach((property, variable) -> message
                .append("  · ").append(variable)
                .append("  (propiedad '").append(property).append("')")
                .append(System.lineSeparator()));

        message.append(System.lineSeparator())
                .append("Definilos en alguno de estos lugares, ninguno versionado:")
                .append(System.lineSeparator())
                .append("  1. .env en la raíz del repo      → cp .env.example .env && chmod 600 .env")
                .append(System.lineSeparator())
                .append("  2. ~/.config/clinics/secrets/sync.yml")
                .append(System.lineSeparator())
                .append("  3. variables de entorno del sistema")
                .append(System.lineSeparator());

        throw new IllegalStateException(message.toString());
    }
}