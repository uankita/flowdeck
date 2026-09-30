# Flowdeck

Collaborative project management — a Kanban board with real-time updates.

Monorepo layout:

| Path        | Stack                                                        |
| ----------- | ------------------------------------------------------------ |
| `backend/`  | Java 21 · Spring Boot 3.3 · Maven · Postgres · Redis · STOMP  |
| `frontend/` | React 18 · TypeScript · Vite · Tailwind · TanStack Query      |
| `docker/`   | docker-compose: Postgres 16, Redis 7                          |

---

## Prerequisites

- JDK 21
- Maven 3.9+
- Node 20+
- A Docker daemon (Docker Desktop, colima, or equivalent) with Compose v2

## Quick start

```bash
cp .env.example .env     # or: make env
make up                  # Postgres + Redis, waits for healthchecks
make backend             # API on http://localhost:8080
make frontend            # UI  on http://localhost:5173
```

`make help` lists every target.

The first two are enough to exercise the API:

```bash
curl -X POST http://localhost:8080/api/v1/boards \
  -H 'Content-Type: application/json' \
  -d '{"boardKey":"FLOW","name":"Flowdeck roadmap"}'

curl http://localhost:8080/api/v1/boards/FLOW
```

Swagger UI is at <http://localhost:8080/swagger-ui.html> (local profile only).

---

## Configuration

A single `.env` at the repo root feeds all three parts:

- **Compose** reads it for container settings and `${...}` interpolation.
  Because the compose file lives in `docker/`, it must be passed explicitly —
  `make` does this for you, or use
  `docker compose --env-file .env -f docker/docker-compose.yml …`.
- **Vite** reads it via `envDir: '..'` in `vite.config.ts`. Only `VITE_`-prefixed
  variables reach browser code.
- **Spring Boot** does *not* read `.env`. The `local` profile's defaults in
  `application.yml` already match the compose defaults, so the backend boots
  with no environment set. Export the variables (or use your runner's env
  support) when you need to override them.

### Profiles

`backend/src/main/resources/application.yml` is one file with three documents:
shared settings, then `local` and `prod`.

- **`local`** (the default) points at `localhost:5432` / `localhost:6379` with
  the same credentials `docker/docker-compose.yml` uses, so a fresh clone works
  with no configuration. SQL logging and Swagger UI are on.
- **`prod`** supplies **no defaults** for hosts or credentials. A missing
  `DB_HOST`, `DB_PASSWORD`, `REDIS_PASSWORD` or `JWT_SECRET` fails startup
  immediately rather than silently falling back to a dev value. It also
  requires `sslmode=require` on Postgres, enables Redis TLS by default, trusts
  forwarded headers, and turns Swagger UI off.

Select one with `SPRING_PROFILES_ACTIVE`.

---

## Testing

```bash
make test            # backend + frontend
make test-backend    # JUnit 5 + Testcontainers (real Postgres 16)
make test-frontend   # tsc --build + vite build
```

`BoardApiIntegrationTest` starts a throwaway Postgres 16 container, lets Flyway
migrate it, and asserts that Hibernate's `ddl-auto: validate` agrees with the
schema — so a migration that drifts from the entities fails the build.

### Docker socket note

Testcontainers does not read Docker *contexts*; it assumes
`/var/run/docker.sock`. Under colima or rootless Docker that path does not
exist, and you get `Could not find a valid Docker environment`. `make
test-backend` resolves the real endpoint from the active context and exports
`DOCKER_HOST` / `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` for you — plain `mvn
test` needs them set yourself.

Separately, `docker.api.version` is pinned to `1.41` in `backend/pom.xml`:
docker-java defaults to API 1.32, which daemons from Docker 28 on reject
outright. Override with `-Ddocker.api.version=…` if your daemon needs another.

---

## Architecture notes

**Card ordering** uses a sparse `double` `position` per lane rather than a
contiguous index, so a drag usually rewrites the one row that moved instead of
renumbering everything after it.

**Optimistic locking** (`@Version`) is on every aggregate. Boards are edited
concurrently, so a stale write should fail loudly rather than silently
overwrite a collaborator's change.

**Board reads** join-fetch `columns` and batch-load `cards`. Fetching both as
`List` in one query throws `MultipleBagFetchException`; `@BatchSize` on
`BoardColumn.cards` keeps it to one extra query for the whole board instead of
one per column.

**State on the frontend** is split deliberately: server data lives in TanStack
Query, and Zustand holds only what the server does not own (what is being
dragged, which card is open, filters). Drag also mirrors columns into local
state, because reordering on every pointer move is too fast to round-trip.

**WebSocket** uses the in-memory simple broker, which is per-instance — two
replicas would not see each other's messages. Redis is already in the stack for
that reason; swap in a relay before scaling past one backend.

---

## Not built yet

This is a working skeleton, not a finished product. The notable gaps:

- **Authentication is a stub.** `flowdeck.auth.*` and a `PasswordEncoder` bean
  exist, but there is no user store and no JWT filter, so `SecurityConfig`
  leaves `/api/v1/**` open. Swap those `permitAll()` calls for
  `authenticated()` as soon as accounts land. Spring Boot logs a generated
  password at startup because no `UserDetailsService` is defined yet.
- **Card create/move is not persisted.** Drag-and-drop reorders the board in
  the browser, but there is no `PATCH /api/v1/cards/{id}/position` endpoint
  behind it, so a reload reverts the move. The `cards` table, entity and DTOs
  are in place.
- **No board events are published.** The frontend subscribes to
  `/topic/boards/{boardKey}` and refetches on any message; nothing broadcasts
  there yet.
