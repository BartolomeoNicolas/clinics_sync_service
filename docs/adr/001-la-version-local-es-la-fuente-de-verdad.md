# ADR-001 · La versión local es la única fuente de verdad de la sincronización

**Estado**: decidido.

## Contexto

El catálogo de la cátedra cambia por bloques: cada versión trae el estado de las entidades
que cambiaron. La cátedra publica tres cosas en Redis: la versión actual, la más vieja que
todavía tiene cambios, y el contenido de cada versión.

Este servicio guarda una copia local del catálogo en PostgreSQL, y esa copia es lo que
responde las búsquedas. La pregunta es cómo se sabe si la copia está al día.

## Decisión

La fuente de verdad es **la versión local guardada en la base**, no la de Redis, no la de la
cátedra. La copia local y su versión se escriben **en la misma transacción**: o quedan las
dos cosas o no queda ninguna.

El historial de versiones aplicadas es **por versión y no un singleton**: guardar solo la
última perdería la capacidad de saber qué ventana se aplicó, que es justo lo que hace falta
para detectar una discontinuidad.

## Consecuencias

- Un fallo a mitad de una versión no deja la versión local adelantada con el catálogo
  atrás: es un estado del que no se sale sin otro snapshot, y por eso no puede existir.
- La búsqueda por profesionales nunca consulta a la cátedra: se resuelve sobre la copia
  local, que es lo que pide el enunciado (§6.2).
- El `snapshotVersion` y la copia van juntos. Por eso el `catalog_version` tiene una fila por
  versión aplicada y no una sola: el historial es lo que permite reconstruir.
- El estado que se consulta por API sale de las mismas fuentes que usa la sincronización: el
  historial de versiones aplicadas y el estado que publica la cátedra. No hay ningún número
  guardado en un lado que pueda quedar viejo.
