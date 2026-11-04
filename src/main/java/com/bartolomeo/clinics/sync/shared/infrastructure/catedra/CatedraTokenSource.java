package com.bartolomeo.clinics.sync.shared.infrastructure.catedra;

/**
 * Proveedor del JWT técnico vigente para hablar con la cátedra.
 *
 * <p><strong>Esta interfaz no es un puerto de dominio y no debe llegar a serlo.</strong> Está en
 * {@code infrastructure} a propósito: el token es un detalle del transporte, y el anexo §5
 * prohíbe que aparezca en repositorios, capturas, documentación, logs ni en la aplicación
 * Android. Un puerto en {@code domain/ports} lo expondría a los casos de uso, que es
 * exactamente lo que se quiere evitar — por eso {@link com.bartolomeo.clinics.sync.sync.domain.ports.out.IntegrationAuthPort}
 * devuelve solo la configuración y guarda el token para sí.
 *
 * <p><strong>Por qué vive en {@code shared/} y no en un contexto.</strong> La cátedra emite un
 * JWT por cuenta técnica y los dos contexts del servicio —{@code sync}, que orquesta, y
 * {@code catalog}, que lee el catálogo— hablan con la misma. Hay una sola caché de sesión en
 * la aplicación y la comparten; si cada contexto tuviera la suya serían dos logins y dos
 * momentos de vigencia que podrían divergir.
 *
 * <p>Colaboración entre adaptadores, no entre capas: quien llama a esto ya es infraestructura
 * ({@code catalog/infrastructure/catedra}), así que el token circula de un adaptador a otro y
 * nunca toca el modelo de dominio.
 *
 * <p>Uso típico: el cliente del snapshot pide el token, lo pone en la cabecera
 * {@code Authorization} y, si recibe un 401, avisa con {@link #invalidate()} antes de reintentar
 * una vez. El reintento es seguro porque {@code GET /api/synchronization/snapshot} es
 * idempotente.
 *
 * @see com.bartolomeo.clinics.sync.catalog.domain.ports.out.CatalogSnapshotPort
 */
public interface CatedraTokenSource {

    /**
     * Token técnico vigente, renovándose solo si hace falta.
     *
     * <p>Puede disparar un login contra la cátedra si no hay sesión o si la que hay está por
     * vencer; quien llama no tiene por qué saberlo.
     *
     * <p><strong>El valor que devuelve es un secreto.</strong> No debe imprimirse, guardarse en
     * un campo de larga vida ni devolverse en una respuesta. Se usa para armar la cabecera y
     * se descarta.
     *
     * @return el JWT técnico actual. Nunca {@code null} ni vacío.
     * @throws com.bartolomeo.clinics.sync.shared.application.exception.ApplicationException si
     *         la cátedra rechaza las credenciales o no se puede entender su respuesta
     */
    String currentToken();

    /**
     * Avisa que el token fue rechazado para que la próxima llamada pida uno nuevo.
     *
     * <p>Se llama cuando la cátedra responde 401 con un token que parecía vigente: puede ser
     * un token revocado o una caché que quedó vieja tras un reinicio. No es un error de
     * red, así que no lanza nada; solo descarta la sesión.
     */
    void invalidate();
}
