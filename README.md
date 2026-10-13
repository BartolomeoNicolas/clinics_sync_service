# clinics_sync_service
Servicio para el integrador final que maneja el catálogo y la sincronización.

## Stack

Java 25 · Spring Boot 4.1.1 · Gradle 9.6.0 · PostgreSQL 16 · Liquibase ·
spring-kafka · spring-data-redis · JPA/Hibernate · springdoc

## Arquitectura

Hexagonal, siguiendo el repo de referencia del profesor `dqmdz/hexagonal`.
Dos bounded contexts:

- **`catalog`** — réplica del catálogo de la cátedra y consultas sobre la copia local.
- **`sync`** — versión local aplicada y orquestación de la sincronización.

```
com.bartolomeo.clinics.sync.<contexto>/
├── domain/            modelo + ports/{in,out}   ← sin Spring, sin JPA
├── application/       usecases/ · service/ · exception/
└── infrastructure/    persistence/ · web/ · messaging/ · catedra/
```

**Lo no obvio:** los ports `in` (interfaces de caso de uso) viven en `domain/ports/in`, no en
`application`. Es contraintuitivo y es la convención del profesor.

## Requisitos

- JDK 25. En esta máquina: `~/Facultad/android-studio/jbr` (el Java del sistema es 21).
- PostgreSQL 16 (lo levanta `compose.yaml`, pendiente de M0-03).

## Cómo correrlo

```bash
export JAVA_HOME="$HOME/Facultad/android-studio/jbr"
./gradlew bootRun
curl localhost:8081/actuator/health
```

La documentación OpenAPI, cuando se abra la seguridad: `/swagger-ui.html`.

## Configuración

`src/main/resources/application.yml`, con perfiles `dev` y `test`.
**Los secretos no van en el repo**: se externalizan a `~/.config/clinics/secrets/` (M0-02).

Todas las variables están documentadas en `.env.example`:

```bash
cp .env.example .env && chmod 600 .env
docker compose up -d      # levanta PostgreSQL 16
./gradlew bootRun         # Liquibase aplica el esquema al arrancar
```

### Base de datos

`docker compose up -d` levanta PostgreSQL 16 con volumen nombrado, red propia en
`10.90.0.0/16` y puerto publicado **solo en loopback**. El usuario de la aplicación
(`sync_app`) no es superusuario y solo puede conectarse a `clinics_sync`.

El esquema lo crea Liquibase desde `src/main/resources/db/changelog/` en cada arranque.
Hibernate está en `ddl-auto: none` y no toca el esquema.

### JWT

Este servicio es **resource server**: valida los tokens que emite el de turnos, con RS256,
firma, vigencia y audiencia. No emite ninguno, y no tiene la clave privada.

La clave pública se lee de una ruta **fuera del repo**, vía `CLINICS_JWT_PUBLIC_KEY_PATH`.

## Errores

Todo error sale en `application/problem+json`. El cliente decide por **`status` + `code`**,
nunca por `detail`: ese puede contener nombres internos y puede cambiar de texto sin romper
a nadie.

```json
{
  "type": "https://dqmdz-um.github.io/prog2/clinics/problems/catalog-unavailable",
  "title": "Catálogo no disponible",
  "status": 503,
  "detail": "El catálogo local todavía no está disponible.",
  "path": "/api/catalog/professionals",
  "message": "Catálogo no disponible",
  "code": "catalog-unavailable",
  "timestamp": "2026-10-13T15:45:29.760653702Z"
}
```

En una validación fallida, `fieldErrors` lista los campos con su mensaje:

```json
"fieldErrors": [
  { "field": "name", "message": "no debe estar en blanco", "rejectedValue": "" }
]
```

### Tabla de códigos

| `code` | Status | Cuándo |
|---|---|---|
| `validation-error` | 400 | Faltan campos o tienen un formato inválido |
| `unauthenticated` | 401 | No vino token, o su firma o audiencia no son válidas |
| `token-expired` | 401 | El token era válido pero venció |
| `forbidden` | 403 | Autenticado, sin permiso sobre el recurso |
| `not-found` | 404 | Ruta inexistente |
| `conflict` | 409 | Conflicto de estado |
| `internal-error` | 500 | Error no previsto. **No incluye detalles internos** |
| `catedra-unavailable` | 502 | La cátedra no respondió o respondió algo ilegible |
| `catalog-unavailable` | 503 | El catálogo local no puede responder |
| `sync-discontinuity` | 503 | La versión local no conecta con la ventana disponible |

Los códigos son **contrato**: no se renombran. Si aparece un fallo que no está en la tabla,
falta agregarlo, no improvisar un texto nuevo.

El enum vive en `application/exception/ErrorCode` —el código del error lo define el
dominio— y el status con el que se responde en
`infrastructure/web/exception/ErrorStatusMapper`. Esa separación es la que mantiene la
regla de que `application` no dependa de `infrastructure`.

## Convenciones de commits

Los commits siguen [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/):

```
tipo(ámbito): descripción
```

| Tipo | Para qué |
|---|---|
| `feat` | funcionalidad nueva |
| `fix` | corrección de defecto |
| `docs` | documentación |
| `test` | pruebas automatizadas |
| `refactor` | cambio sin alterar comportamiento |
| `chore` | tooling, configuración, dependencias |

**Ámbitos sugeridos en este repo**: sync, infra, docs, adrs

Ejemplos:

```
    feat(sync): aplica el snapshot completo en una transaccion
    fix(sync): avanza la version local solo si cerro la unidad de trabajo
    feat(catalog-api)!: cambia el contrato de busqueda de profesionales
```

Reglas adicionales:

- El tipo va en inglés (lo entienden las herramientas estándar); la descripción en español.
- La línea de asunto tiene máximo 72 caracteres y no termina en punto.
- Si un commit cierra una issue, se indica `Closes #N` en el cuerpo; si solo la menciona, `Refs #N`.
- Un `!` antes de los dos puntos (`feat(api)!:`) marca un cambio incompatible del contrato.
- Commits generados por Git (`Merge`, `Revert`, `squash!`, `fixup!`) se aceptan sin validar.

### Validación automática

El hook `scripts/git-hooks/commit-msg` rechaza los commits que no cumplen el formato.
Está versionado en este repo y se activa en cada clon con:

```bash
git config core.hooksPath scripts/git-hooks
```

Para saltearlo a propósito: `git commit --no-verify` (no se recomienda).
