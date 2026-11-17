# ADRs

Las decisiones que explican por qué el servicio está como está. Cada una tiene contexto,
decisión y consecuencias, para que se pueda leer sin el código al lado.

| # | Decisión | Estado |
|---|---|---|
| [001](001-la-version-local-es-la-fuente-de-verdad.md) | La versión local es la única fuente de verdad de la sincronización | decidido |
| [002](002-se-reconstruye-cuando-la-ventana-no-alcanza.md) | Se reconstruye con snapshot cuando la ventana no alcanza | decidido |
| [003](003-el-acl-de-la-catedra.md) | El ACL de la cátedra es de lectura y el namespace propio es de escritura | decidido |
| [004](004-problem-json.md) | Los errores se traducen con `@RestControllerAdvice` y `problem+json` | decidido (desviación del repo de referencia) |
| [005](005-el-listener-no-pasa-por-la-fachada.md) | El consumidor de avisos invoca el port `in` directo | decidido (desviación del repo de referencia) |

Las desviaciones del repo de referencia del profesor (004 y 005) están justificadas por el
contrato con la cátedra: el contrato manda sobre la convención cuando chocan.
