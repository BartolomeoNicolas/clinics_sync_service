package com.bartolomeo.clinics.sync.sync.infrastructure.catedra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de {@code POST /api/authenticate} (anexo §5.2): el JWT técnico y la integración.
 *
 * <p>{@code id_token} va con {@code @JsonProperty} porque el contrato lo trae en snake_case
 * (mismo caso que la respuesta OAuth2 del booking, ver E-18): la convención del lenguaje se
 * respeta en el código y la del contrato, en el cable.
 *
 * <p>Enmascara el token en {@link #toString()}: un record imprime todos sus campos y este token
 * no tiene por qué terminar en un log.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CatedraAuthenticationResponse(
        @JsonProperty("id_token") String idToken,
        CatedraIntegrationResponse integration) {

    @Override
    public String toString() {
        return "CatedraAuthenticationResponse[idToken=***, integration=%s]".formatted(integration);
    }
}
