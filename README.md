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

The first two are enough to exercise the auth flow end to end:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@flowdeck.dev","password":"correct horse battery staple","displayName":"Ada Lovelace"}'
# => {"accessToken": "...", "refreshToken": "...", "accessTokenExpiresInSeconds": 900, "user": {...}}

curl http://localhost:8080/api/v1/me -H "Authorization: Bearer <accessToken>"
```

The board API additionally needs a workspace, and there's no endpoint to
create one yet (see [Not built yet](#not-built-yet)) — for now that means a
direct DB insert (or a `WorkspaceRepository.save(...)` call from a test).

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
make test-backend    # JUnit 5 + Testcontainers (real Postgres 16, real Redis 7)
make test-frontend   # tsc --build + vite build
```

`BoardApiIntegrationTest` starts a throwaway Postgres 16 container, lets Flyway
migrate it, and asserts that Hibernate's `ddl-auto: validate` agrees with the
schema — so a migration that drifts from the entities fails the build. It
mints access tokens directly via `AccessTokenService` rather than going
through `/api/auth/login`, since it isn't testing auth itself — that's
`AuthApiIntegrationTest`'s job, and the one test class in this project that
starts a Redis container too (a plain `redis:7-alpine` `GenericContainer`
wired up via `@DynamicPropertySource`, not `@ServiceConnection` — there's no
official Testcontainers module for plain Redis). It covers the full
`/api/auth/**` surface: successful login, bad credentials, an expired access
token, refresh rotation, and — the one worth reading if you read only one —
reuse of a rotated refresh token, which proves the *entire family* is revoked
by rotating a token three times, replaying the middle one, and then showing
that the third (never reused, still "current") token is dead too.

`RankGeneratorTest` is a plain unit test (no Spring context, no database) —
sorting/uniqueness properties across randomized and adversarial insert
patterns, `spacedRanks` staying short even for thousands of items, and the
exhaustion boundary itself: it drives the same-gap-squeeze pattern until
`between` throws, then asserts every rank generated up to that point still
respects the length limit and sorts correctly. `CardMoveIntegrationTest`
covers the same exhaustion path one layer up — through real HTTP and
Postgres — along with the "moving a card writes only that card's row" claim,
checked by asserting untouched siblings' `@Version` is byte-for-byte
unchanged after a move.

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

**Hierarchy** is `Workspace → Board → BoardList → Card`, with `WorkspaceMember`
attaching a `User` to a `Workspace` with a role (`OWNER`/`ADMIN`/`MEMBER`/`VIEWER`).
Boards and cards are soft-deletable (`deletedAt`, filtered automatically via
Hibernate `@SQLRestriction`); `BoardList` and `Card` carry `@Version` for
optimistic locking, since concurrent drag-and-drop is exactly where a lost
update would happen.

**Board reads** join-fetch `lists` and batch-load `cards`. Fetching both as
`List` in one query throws `MultipleBagFetchException`; `@BatchSize` on
`BoardList.cards` keeps it to one extra query for the whole board instead of
one per list.

### Card ordering

`PATCH /api/v1/workspaces/{workspaceId}/cards/{cardId}/move` — `CardService`,
behind the same `@PreAuthorize` workspace-role check as the board API.

- **Rank strings, not integer positions** (`com.flowdeck.util.RankGenerator`,
  LexoRank-style): moving a card writes only that card's row, with a rank
  computed to sit between its new neighbours, rather than renumbering
  everything after it. `BoardList` uses the same scheme for list order.
- **The move request names two neighbours**, not a numeric index — the ids of
  the cards it should land between (either or both may be omitted, for "top
  of the list" / "bottom of the list"). Both must actually belong to the
  target list, and neither may be the card being moved; violating either is a
  400, not a 500.
- **A caller can't move a card through, into, or out of a workspace they
  don't belong to.** `workspaceId` comes from the URL and gates `@PreAuthorize`
  same as boards, but the service *also* checks that both the card being
  moved and the target list actually belong to that workspace — otherwise a
  legitimate member of workspace A could name a `targetListId` (or a
  neighbour id) belonging to workspace B in the request body and the
  `@PreAuthorize` check alone would never catch it, since it only inspects
  the path.
- **Rank exhaustion rebalances automatically.** Repeatedly inserting at the
  exact same point eventually leaves no string short enough to fit between
  two neighbours (`RankGenerator.MAX_RANK_LENGTH`, ~55 consecutive same-gap
  inserts in the worst case) — `RankGenerator.between` signals that by
  throwing `RankExhaustionException` rather than growing forever.
  `CardService` catches it, rewrites the whole list to short, evenly-spaced
  ranks (`RankGenerator.spacedRanks`, generated by bisection so results stay
  O(log₃₆ n) characters long), and retries the original move — all inside the
  one transaction, so the rebalance and the move it made room for commit (or
  fail) together. `RankGeneratorTest` covers the algorithm exhaustively in
  isolation; `CardMoveIntegrationTest` proves the same path end to end
  against real Postgres, by deliberately squeezing a gap to the exhaustion
  boundary and asserting the move still succeeds.

### Authentication

JWT access tokens (15 min, `flowdeck.auth.access-token-expiry-minutes`) plus
opaque refresh tokens stored in Redis (30 days,
`flowdeck.auth.refresh-token-expiry-days`), with rotation and reuse detection:

- **`POST /api/auth/register`, `/login`, `/refresh`, `/logout`** — all under
  `permitAll()`; none of them go through the JWT filter, since
  register/login have no token yet and refresh/logout carry their own
  credential (the refresh token) in the request body.
- **Login is constant-time** regardless of whether the email exists —
  `AuthService` always runs a BCrypt comparison, against a precomputed dummy
  hash when the user isn't found, so response timing can't be used to
  enumerate registered emails.
- **Refresh tokens are opaque**, not JWTs — just a random 256-bit string used
  directly as a Redis key. There's nothing to gain from making a
  server-validated, Redis-backed token self-describing.
- **Rotation with reuse detection** (`com.flowdeck.security.RefreshTokenService`):
  every refresh token belongs to a *family* (the chain from one login). Using
  one issues a new token in the same family and marks the used one `ROTATED`
  — not deleted, so a later replay is recognisable as reuse rather than
  looking like an ordinary expired token. Reuse revokes the *whole family*,
  including whatever is currently the legitimate active token, on the
  reasoning that an old token being replayed could mean either a client retry
  or a theft, and those can't be told apart — so the full chain is burned
  rather than trusting the most recent link. See `AuthApiIntegrationTest` for
  this proven end to end.
- **`@CurrentUser`** (`com.flowdeck.security`) resolves an
  `AuthenticatedUser` (id + email) controller parameter from the verified
  token's claims — see `MeController` for the minimal example.
- **Workspace-role authorization** is method-level, not path-level:
  `@PreAuthorize("@workspaceAuthorization.hasAtLeastRole(#workspaceId, T(...).MEMBER)")`
  on `BoardController`. `WorkspaceAuthorization` re-checks `workspace_members`
  from Postgres on every call rather than trusting anything baked into the
  access token — roles can change mid-session, and 15 minutes is long enough
  for a stale cached role to matter.
- **Access tokens carry only identity** (`sub`, `email`) for the same reason
  — no roles, no permissions, just enough to look the rest up.

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

- **No Workspace API.** `POST /api/auth/register` creates a user, but nothing
  creates a workspace or the first `WorkspaceMember` (`OWNER`) row for
  it — that only happens in tests, via direct repository calls. Until a
  `WorkspaceController` exists, getting a real workspace to point the board
  API at means inserting one by hand.
- **No card create/delete endpoint**, only move. `PATCH .../cards/{id}/move`
  exists (see [Card ordering](#card-ordering)) and persists correctly, but
  there's still no way to create, edit, or delete a card through the API —
  only via direct repository calls, as the tests do.
- **No board events are published.** The frontend subscribes to
  `/topic/boards/{boardKey}` and refetches on any message; nothing broadcasts
  there yet, including on a card move. It also still expects the pre-auth,
  pre-redesign API shape (`columns`/`position`/single `assignee`, unscoped
  board routes, no `Authorization` header) — it needs a follow-up pass to
  work against the
  current backend at all.
- **No access-token revocation.** Logout kills the *refresh* session — the
  still-live access token (up to 15 min old) keeps working until it expires
  naturally, the standard trade-off for stateless JWTs. A `jti` denylist in
  Redis would close that window if it's ever needed.
