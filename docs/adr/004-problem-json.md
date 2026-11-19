# ADR-004 · Los errores se traducen con `@RestControllerAdvice` y `problem+json`

**Estado**: decidido. Es la única convención del repo de referencia que **no** adoptamos, y
es deliberado.

## Contexto

El repo de referencia del profesor traduce la excepción a HTTP en el cuerpo del método del
controller, con `try/catch` y `ResponseStatusException`. Es su convención y la seguimos en
todo lo demás: los ports `in` en `domain`, el modelo sin framework, los mappers null-safe, la
fachada, la regla del `Optional`.

El contrato con la cátedra (anexo §13) dice otra cosa: el cliente decide por `status` +
`code`, nunca por el texto de `detail`, y exige `application/problem+json` con un `code`
estable.

## Decisión

Adoptamos **el contrato**: un `@RestControllerAdvice` único en `shared` traduce cada
excepción de aplicación a `problem+json` con un `code` estable y un status. El controller no
atrapa excepciones.

Cada error de negocio tiene su propia clase de excepción en
`<contexto>/application/exception`, con su `ErrorCode` fijo. El `ErrorStatusMapper` es un
mapa **cerrado a propósito**: si aparece un código sin status, la aplicación falla al
arrancar en vez de inventarse uno.

## Consecuencias

- El `ResponseStatusException` de Spring convierte un error en un cuerpo genérico **sin
  `code`**: eso no cumple el contrato. Es el motivo de la desviación.
- El controller queda más delgado: traduce el resultado y nada más.
- Un error nuevo necesita tres cosas: la excepción, su código y su status. Si falta la
  tercera, la aplicación no arranca: es el precio de que el mapa sea cerrado.
- Los errores que la cátedra puede devolver y este servicio no (porque pertenecen a
  endpoints que no llama) están en el README con la razón de por qué no tienen fila.
