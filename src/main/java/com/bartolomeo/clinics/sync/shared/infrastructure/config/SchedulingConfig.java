package com.bartolomeo.clinics.sync.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas del servicio.
 *
 * <p><strong>Está en un {@code @Configuration} y no en la clase principal.</strong> El
 * planificador es una preocupación transversal —le sirve al servicio entero, no a un
 * contexto— y por eso va en la infraestructura compartida, donde ya viven la seguridad, los
 * errores y el reloj.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
