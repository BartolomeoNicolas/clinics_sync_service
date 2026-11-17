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
| `catedra-rejected` | 503 | La cátedra rechazó nuestras credenciales técnicas. Un reintento no lo arregla: hay que corregirlas |
| `catalog-unavailable` | 503 | El catálogo local no puede responder |
| `sync-discontinuity` | 503 | La versión local no conecta con la ventana disponible |

Los códigos son **contrato**: no se renombran. Si aparece un fallo que no está en la tabla,
falta agregarlo, no improvisar un texto nuevo.

El enum vive en `application/exception/ErrorCode` —el código del error lo define el
dominio— y el status con el que se responde en
`infrastructure/web/exception/ErrorStatusMapper`. Esa separación es la que mantiene la
regla de que `application` no dependa de `infrastructure`.

## Sincronización

El catálogo se mantiene al día en dos pasos, y ninguno de los dos depende de que el otro
llegue: Kafka **notifica**, Redis **guarda**.

```
Kafka (catedra.catalog.{groupId})        Redis (catedra:sync:*)
        │  "hay una versión 7"                    │
        ▼                                          ▼
  el consumidor  ──────► compara versiones ──────► changes:7 + el estado vigente
        │                                          │
        │  aplica los cambios, de a una versión    │
        ▼                                          ▼
  PostgreSQL: la copia local + el historial de versiones aplicadas
```

- **El aviso no lleva los datos.** Kafka solo dice qué versión hay disponible; lo que se
  aplica se decide comparando la versión local con la que publica la cátedra. Por eso
  perder un aviso no pierde datos: el siguiente, o la comprobación de versiones, recupera
  el atraso.
- **Los cambios se aplican en orden**, de a una versión, y **la versión local avanza en la
  misma transacción** que los cambios. Si el registro de la versión se confirmara aparte, un
  fallo a mitad de una versión dejaría la versión local adelantada y el catálogo atrás.
- **Una discontinuidad se reconstruye.** Si la versión local es menor que la más vieja
  disponible (`oldest-available-version`), faltan los incrementos que la conectan y se pide
  un snapshot REST en vez de intentar encadenar (anexo §14.2).
- **Un lock distribuido** en `alumnos:{groupId}:locks:sync` evita que dos sincronizaciones se
  pisen. Se toma con vencimiento, para que se libere solo si el proceso muere.
- **El estado y los errores se consultan por API**, sin abrir la base:
  - `GET /api/sync/status` — versión local, versión remota, ventana disponible, si hay una
    sincronización en curso y cómo terminó la última.
  - `GET /api/sync/errors` — los errores recientes, del más nuevo al más viejo.

### Evidencia

| Evidencia del §11 | Dónde se prueba |
|---|---|
| 1 · inicialización desde snapshot | `SyncFullSnapshotIntegrationTest` (PostgreSQL + WireMock) |
| 2 · actualización incremental | `SyncIncrementalIntegrationTest` (PostgreSQL + Redis) |
| 3 · recuperación por snapshot tras una discontinuidad | `SyncIncrementalIntegrationTest` (el caso del anexo §18.2) |
| 4 y 5 · búsqueda y filtros sobre la copia local | `CatalogSearchIntegrationTest` |
| 9 · mensaje duplicado | `CatalogUpdatedKafkaIntegrationTest` (Kafka real) y `ProcessedEventPersistenceTest` |

`./gradlew clean build` corre toda la suite sin acceso a la red de la cátedra: lo que no se
puede probar sin la VPN —el ACL real de `catedra:sync:*`— queda como evidencia local con
Redis y ACL equivalentes (`CatalogChangesRedisTest`).

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
