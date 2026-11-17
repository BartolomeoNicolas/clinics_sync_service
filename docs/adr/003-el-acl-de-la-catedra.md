# ADR-003 · El ACL de la cátedra es de lectura, y el namespace propio es de escritura

**Estado**: decidido.

## Contexto

El catálogo vive en Redis, en el namespace de la cátedra (`catedra:sync:*`). Este servicio
necesita leer ese namespace —los cambios, la metadata y el estado de las entidades— y
necesita escribir en el suyo: el lock de la sincronización y el diagnóstico de la última
sincronización.

El anexo §14.1 del contrato describe los permisos de la cuenta técnica: lectura sobre
`catedra:sync:*` y lectura y escritura sobre `alumnos:{groupId}:*`, sin acceso a
`catedra:holds:*`, que es interno de la cátedra.

## Decisión

Se respeta el ACL tal como lo define el contrato, y el servicio **no pide ni un permiso
más**:

- El namespace de la cátedra es **de solo lectura**, y el único permiso que se usa es `GET`.
- El namespace propio (`alumnos:{groupId}:*`) es de lectura y escritura, y ahí viven el
  lock (`locks:sync`) y el diagnóstico (`diagnostics:last-sync`).
- Los permisos se piden con **selectores** de ACL de Redis 7, porque con un solo juego de
  permisos globales no se puede dar lectura en un namespace y lectura más escritura en otro.

## Consecuencias

- El test de ACL levanta un Redis real con las tres reglas del anexo y comprueba que se
  puede leer lo que la sincronización necesita y que **no** se puede escribir en
  `catedra:sync:*` ni leer `catedra:holds:*`.
- El lock es un `SET ... NX PX`: el candado se toma con vencimiento y con el identificador
  del proceso como valor, para que un proceso no pueda soltar el lock de otro. Sin
  vencimiento, un proceso que muere lo dejaría tomado para siempre.
- El diagnóstico vive en Redis y no en la base: es el estado del proceso que mantiene al día
  la copia, no información de negocio del catálogo. Consultarlo no requiere credenciales de
  base.
- El ACL real de la cátedra **no está verificado**: eso necesita la VPN. El sustituto
  local con ACL equivalente es evidencia local, y está escrito como tal.
