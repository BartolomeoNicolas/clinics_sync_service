package com.bartolomeo.clinics.sync;

import com.bartolomeo.clinics.sync.infrastructure.config.RequiredSecretsInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de catálogo y sincronización.
 *
 * <p>Bounded contexts: {@code catalog} (réplica del catálogo de la cátedra y consultas) y
 * {@code sync} (versión local y orquestación de la sincronización).
 *
 * <p>La estructura de cada contexto sigue las convenciones del repo de referencia del profesor
 * {@code dqmdz/hexagonal}: los ports {@code in} viven en {@code domain/ports/in}, no en
 * {@code application}. Ver {@code docs/hexagonal-profesor.md}.
 */
@SpringBootApplication
public class SyncServiceApplication {

    public static void main(String[] args) {

        // Corre antes de crear beans: si falta un secreto, el arranque para acá
        // nombrando la variable, en vez de morir en el driver de la base.
        SpringApplication app = new SpringApplication(SyncServiceApplication.class);
        app.addInitializers(new RequiredSecretsInitializer());
        app.run(args);
    }
}