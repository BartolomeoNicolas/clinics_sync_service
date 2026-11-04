package com.bartolomeo.clinics.sync.sync.domain.ports.out;

import com.bartolomeo.clinics.sync.sync.domain.model.IntegrationConfig;

/**
 * Puerto de salida por el que el contexto {@code sync} obtiene la integración técnica de la
 * cátedra (anexo §5.2 y §5.3).
 *
 * <p>No es una operación de negocio: es la puerta hacia el servicio central que nos entrega el
 * catálogo. Por eso está en {@code ports/out} y no en {@code in}: el dominio la <em>pide</em>,
 * nunca la expone.
 *
 * <p><strong>El token técnico no sale de acá.</strong> La operación devuelve la configuración,
 * que es lo que el negocio necesita; el JWT que la respalda es un detalle del transporte y se
 * queda dentro del adaptador. Así ningún caso de uso puede terminar logueándolo o devolviéndolo
 * en una respuesta (anexo §5: <em>"el JWT no debe aparecer en repositorios, capturas,
 * documentación, logs ni en la aplicación Android"</em>).
 *
 * <p><strong>El {@code groupId} viene de la configuración que devuelve la cátedra</strong>, que
 * está atada al token, y nunca de la configuración local ni del body de una petición (§6).
 *
 * <p>Fallos: las excepciones de aplicación con los códigos
 * {@code catedra-rejected} (las credenciales técnicas fueron rechazadas, que es un problema del
 * operador y no de la red) y {@code catedra-unavailable} (la cátedra no respondió o respondió
 * algo que no se pudo entender).
 *
 * @see com.bartolomeo.clinics.sync.sync.domain.model.IntegrationConfig
 */
public interface IntegrationAuthPort {

    /**
     * Devuelve la configuración vigente de la integración técnica.
     *
     * <p>Puede llamar a la cátedra si no hay un token vigente en memoria; los usuarios de este
     * puerto no tienen por qué saberlo.
     *
     * @return la integración atada al JWT técnico actual. Nunca {@code null}.
     * @throws com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException si la
     *         cátedra rechaza las credenciales o no se puede entender su respuesta
     */
    IntegrationConfig currentIntegration();
}
