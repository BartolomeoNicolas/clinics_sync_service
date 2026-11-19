# ADR-002 · Se reconstruye con snapshot cuando la ventana no alcanza

**Estado**: decidido.

## Contexto

La cátedra retiene los cambios de las últimas versiones. Cuando este servicio lleva un
tiempo sin sincronizar, la versión que conectaría su estado local con la ventana disponible
puede ya no estar: los cambios se perdieron y no hay forma de encadenar el incremento.

El contrato lo dice sin rodeos (anexo §14.2): si la versión local es menor que la más
vieja disponible, no están todos los incrementos necesarios y hay que obtener un snapshot
REST. El anexo §18.2 lo repite con números: con la versión local en 3 y la ventana
empezando en 4, **no** se intenta leer `changes:4`.

## Decisión

La comprobación va **antes** de aplicar nada: si la versión local es menor que
`oldest-available-version`, se pide el snapshot y se reconstruye la copia entera. No se
intenta un incremento parcial.

Un incremento que no se puede encadenar no se intenta "a ver qué pasa": se sabe que va a
fallar y se reconstruye.

## Consecuencias

- La comprobación cuesta una lectura del estado de Redis y evita un incremento a medias que
  dejaría la copia con una mezcla de versiones que nunca existió.
- Reconstruir es más caro que un incremento: se baja el catálogo entero y se reemplaza la
  copia. Es el precio de que la copia sea consistente.
- La versión local nunca queda atrás de la ventana sin que se note: el `snapshotVersion`
  guardado es exactamente el de los datos almacenados.
- El diagnóstico dice por qué se reconstruyó: el barrido deja el motivo en el log y el
  endpoint de errores lo muestra.
