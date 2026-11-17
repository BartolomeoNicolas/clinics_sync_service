package com.bartolomeo.clinics.sync.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.bartolomeo.clinics.sync.sync.infrastructure.redis.RedisSyncDiagnosticsAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las claves del namespace propio.
 *
 * <p>La cátedra entrega el namespace con el comodín final —es el patrón del ACL— y
 * concatenarlo tal cual dejaría un asterisco literal en el medio de la clave, que no es la
 * clave que dice el contrato (§14.6) ni la que esperaría un operador mirando Redis.
 */
@DisplayName("las claves del namespace propio de Redis")
class RedisSyncDiagnosticsAdapterTest {

    @Test
    @DisplayName("el namespace con comodin final se convierte en prefijo")
    void elComodinFinalSeSaca() {
        assertThat(RedisSyncDiagnosticsAdapter.claveDe(
                "alumnos:nicobartok0-prog2:*", ":diagnostics:last-sync"))
                .isEqualTo("alumnos:nicobartok0-prog2:diagnostics:last-sync");

        assertThat(RedisSyncDiagnosticsAdapter.claveDe(
                "alumnos:nicobartok0-prog2:*", ":locks:sync"))
                .isEqualTo("alumnos:nicobartok0-prog2:locks:sync");
    }

    @Test
    @DisplayName("un namespace sin comodin se usa tal cual")
    void unNamespaceSinComodinSeUsaTalCual() {
        assertThat(RedisSyncDiagnosticsAdapter.claveDe(
                "alumnos:nicobartok0-prog2", ":locks:sync"))
                .isEqualTo("alumnos:nicobartok0-prog2:locks:sync");
    }

    @Test
    @DisplayName("un namespace con solo el asterisco tambien se recorta")
    void unNamespaceConSoloElAsteriscoSeRecorta() {
        assertThat(RedisSyncDiagnosticsAdapter.claveDe(
                "alumnos:nicobartok0-prog2*", ":locks:sync"))
                .isEqualTo("alumnos:nicobartok0-prog2:locks:sync");
    }
}
