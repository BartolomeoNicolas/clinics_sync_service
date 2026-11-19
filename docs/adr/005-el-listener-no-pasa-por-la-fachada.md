# ADR-005 · El consumidor de avisos invoca el port `in` directo

**Estado**: decidido. Es la segunda desviación del repo de referencia, también deliberada.

## Contexto

En el repo de referencia, todo pasa por la fachada: los controllers la inyectan y los
consumidores de mensajes también. La regla que el profesor escribe es "todo pasa por la
fachada".

Pero una fachada de servicio tiene sentido para lo que un cliente **pide**: listar, buscar,
crear. Un listener de Kafka no pide nada: reacciona a algo que ya pasó. Y una fachada que
mezclara las dos cosas tendría un método para "aplicar un aviso" que no es una operación de
servicio sino una reacción.

## Decisión

El listener de `CatalogUpdated` invoca **el port `in` directamente**, sin pasar por la
fachada. El resto —los controllers— sigue pasando por la fachada, como manda el repo de
referencia.

Además, el aviso de Kafka **no dice qué hay que hacer**: dice qué versión hay disponible. Lo
que se aplique lo decide la comparación entre la versión local y la que hay en Redis. Por eso
el listener llama al caso de uso de sincronización incremental y no a uno que aplique el
cambio que trae el mensaje.

## Consecuencias

- Perder un aviso **no pierde datos**: el aviso es notificación, no fuente de datos. El
  siguiente aviso, o la comprobación de versiones, recupera el atraso. Es lo que el
  enunciado (§6.2) pide y lo que hace que este diseño sea barato de operar.
- La idempotencia va por el `eventId`, que el contrato define como la clave funcional, y
  queda guardada en `processed_event` con el `event_id` como clave primaria: la unicidad la
  impone la base y no solo el código.
- El offset se confirma a mano y **después** de persistir. Si se confirmara antes, un corte
  entre la confirmación y el guardado perdería el aviso para siempre.
- Lo que no se puede arreglar —un mensaje ilegible, una versión de contrato desconocida— se
  registra en `sync_error` y se confirma. Lo que sí se puede arreglar se deja pasar hacia el
  contenedor, que reintenta con un tope, sin confirmar el offset.
