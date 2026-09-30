# Flowdeck

Collaborative project management — a Kanban board with real-time updates.

Monorepo layout:

| Path        | Stack                                                        |
| ----------- | ------------------------------------------------------------ |
| `backend/`  | Java 21 · Spring Boot 3.3 · Maven · Postgres · Redis · STOMP  |
| `frontend/` | React 18 · TypeScript · Vite · Tailwind · TanStack Query · axios |
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

The first two are enough to walk the whole API by hand:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@flowdeck.dev","password":"correct horse battery staple","displayName":"Ada Lovelace"}'
# => {"accessToken": "...", "refreshToken": "...", "accessTokenExpiresInSeconds": 900, "user": {...}}

TOKEN=<the accessToken above>

curl -X POST http://localhost:8080/api/v1/workspaces -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"slug":"acme","name":"Acme Corp"}'
# => {"id": "...", ...} — the caller is now this workspace's OWNER

curl -X POST http://localhost:8080/api/v1/workspaces/<id>/boards -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"boardKey":"FLOW","name":"Flowdeck roadmap"}'
# => a board seeded with Backlog/In progress/Done lists
```

`make frontend` walks the same register → create workspace → create board flow through the UI — see [Frontend](#frontend) for what that actually covers today (and, just as importantly, doesn't yet).

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

`CardApiIntegrationTest` covers create/read/update/delete/list/labels for
cards. Its headline case, `concurrentUpdatesRaceAndExactlyOneWins`, is a
*real* concurrent-update test — two threads via `ExecutorService`, not two
sequential requests pretending to race — both reading version 0 before
either writes, both `PATCH`ing with it. That's deterministic despite being
genuine concurrency: Postgres only ever lets one `UPDATE ... WHERE id = ?
AND version = 0` succeed for a given row, regardless of how the two
threads' statements happen to interleave, so exactly one request gets 200
and the other 409 every time — five back-to-back runs during development
confirmed it, not just one. A simpler sequential test
(`updateWithAStaleVersionReturns409WithCurrentServerState`) checks the same
contract deterministically, for a clearer read on what "current server
state" in the 409 body actually looks like.

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

### REST API

Five resources, one consistent shape: `WorkspaceController` → `WorkspaceService`
→ `WorkspaceRepository`, and the same three-layer split for boards, lists,
cards, and labels. Controllers never see an entity — every request and
response is a `record` in `web.dto`, projected by a MapStruct `@Mapper`
(`BoardMapper`, `WorkspaceMapper`, `LabelMapper`) that fails the build
(`unmappedTargetPolicy=ERROR`) if a response field is ever left unmapped.

- **Every list endpoint is paginated** — `Pageable` in, `PageResponse<T>`
  out (`content`/`page`/`size`/`totalElements`/`totalPages`), not Spring
  Data's own `Page` JSON, which carries internal-looking fields like
  `pageable`/`sort`. The one exception is a card's own labels
  (`GET .../cards/{id}/labels`): inherently a handful of items, a picker
  list, not worth paginating. springdoc generates a distinct schema per
  instantiation (`PageResponseCardResponse`, `PageResponseLabelResponse`,
  …) without any extra annotation — confirmed by actually reading
  `/v3/api-docs`, not just assuming a generic record would render cleanly.
- **Bean Validation on every request DTO** (`@NotBlank`, `@Size`, `@Pattern`
  for things like the hex-color and board-key formats, `@Positive` on WIP
  limits), surfaced as 400 via `GlobalExceptionHandler`'s
  `MethodArgumentNotValidException` handler.
- **Every error is `application/problem+json`** (RFC 7807) via
  `@RestControllerAdvice` — not just auth failures (see
  [Authentication](#authentication)) but every domain exception: not-found,
  duplicate-name/key/slug, invalid-move, version conflicts.
- **Optimistic lock conflicts return 409 with the current server state in
  the body.** `Card` and `BoardList` updates take the `version` the client
  last read; a mismatch — checked explicitly before touching anything, and
  backstopped by `@Version` itself at flush time for a genuine race — throws
  `CardVersionConflictException`/`BoardListVersionConflictException`, and
  the handler attaches the fresh entity as an RFC 7807 *extension member*
  (`problem.setProperty("currentState", ...)`) so the client can resolve the
  conflict without a second round trip. Getting the "fresh" part right
  needed a real fix: naively re-`findById`-ing after a failed write returns
  the *same* managed instance from the persistence context's first-level
  cache — still carrying the failed edit — not the database's true row;
  `entityManager.refresh(entity)` is what actually re-syncs it. Proven both
  ways in `CardApiIntegrationTest` — see [Testing](#testing).
- **A caller-supplied move that doesn't reflect reality fails cleanly, not
  with a raw constraint violation.** If a move names a `previous`/`next`
  pair that isn't actually adjacent (something else already sits between
  them), the computed rank collides with an existing one at the database's
  unique index — caught and reported as 400
  (`InvalidCardMoveException`/`InvalidListMoveException`), not left to
  surface as an unmapped `DataIntegrityViolationException`. Found via live
  `curl` testing, not by design — worth calling out since it's exactly the
  kind of gap integration tests with only "correct" inputs won't catch.

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

**WebSocket** uses the in-memory simple broker, which is per-instance — two
replicas would not see each other's messages. Redis is already in the stack for
that reason; swap in a relay before scaling past one backend.

---

## Frontend

Auth (login/register), protected routing, a workspace switcher, and a board
list page — `src/features/{auth,workspaces,boards}`, colocated by feature
rather than split into parallel `pages/`/`hooks/`/`api/` trees. The one
exception is `src/components/`: generic design-system primitives (`Button`,
`TextField`, `Skeleton`, `Dialog`, `Pagination`, …) that every feature
consumes aren't *a* feature themselves, so they stay shared rather than
duplicated or arbitrarily owned by whichever feature used them first.

- **The API client is typed from the backend's own OpenAPI spec**, not
  hand-duplicated interfaces: `npm run openapi:sync` re-fetches
  `/v3/api-docs` into the committed `openapi.json` snapshot and regenerates
  `src/api/schema.ts` via `openapi-typescript`. Feature `api.ts` modules
  reference `components['schemas'][...]` directly (`Required<...>` where
  the field is one the backend always actually sends — springdoc doesn't
  mark record fields required even when the Java type is non-null) rather
  than routing through the generated `paths` type: springdoc's schema
  exposes response bodies under a wildcard `*/*` content type (no
  `produces` is declared on the controllers) and incorrectly lists
  `@CurrentUser` as a query parameter (it's not springdoc-aware), both of
  which a naive generic path-indexed client would have to work around —
  going straight to the flat schema names sidesteps both.
- **The access token lives in memory only** — never `localStorage`/
  `sessionStorage` — so a reload always starts without one. The refresh
  token *is* persisted to `localStorage` (`api/tokenStore.ts`'s Javadoc
  spells out that trade-off), which is what lets `AuthBootstrap` silently
  restore a session on load instead of forcing a fresh login every reload;
  a revoked or invalid refresh token fails that attempt cleanly and lands
  on `/login` (verified by hand: poisoning the stored refresh token and
  reloading redirects correctly and clears the dead token).
- **A 401 triggers exactly one single-flighted refresh-and-retry**
  (`api/client.ts`'s response interceptor, `api/authRefresh.ts`'s
  in-flight-promise dedup). The single-flighting isn't just an efficiency
  nicety: the backend rotates refresh tokens on every use and treats a
  second use of an already-rotated token as reuse, revoking the *entire*
  session family (see [Authentication](#authentication)) — two concurrent
  401s naively both calling `/api/auth/refresh` with the same stored token
  would trip that on a perfectly innocent user.
- **Dark mode is CSS variables, not a `dark:` prefix on every utility.**
  `index.css` defines each design token (`--color-surface`, `--color-ink`,
  …) twice, once under `:root` and once under `.dark`; `tailwind.config.js`
  points color names at the variables. `bg-surface` and `text-ink-muted`
  just work in both themes with no component-level `dark:` variant needed
  anywhere. An inline script in `index.html` applies the class before first
  paint to avoid a flash of the wrong theme; `useDarkMode` takes over from
  there and follows OS-level changes live until the user makes an explicit
  choice, which then wins permanently.
- **Every list endpoint's loading state is a shaped skeleton**
  (`components/Skeleton.tsx`), not a spinner — it reserves the actual
  layout's space so nothing jumps when real content arrives. Verified by
  throttling the network in a real browser and catching the board grid and
  workspace-switcher skeletons mid-flight, not just by reading the
  conditional-rendering code.
- **Toasts** are `sonner`, theme-synced to the same dark-mode state as
  everything else, fired from mutation `onSuccess`/`onError` handlers.

**Verified end to end in a real (headless) browser**, not just typechecked:
register → create workspace → create board → click into the (placeholder —
see below) board page → toggle dark mode → **reload the page and confirm the
session survives** → log out → log back in → wrong password shows the
backend's exact constant-time error message. `npm run typecheck`, `npm run
lint` (a working `eslint.config.js` didn't exist before this pass either —
the `lint` script was wired up with no config behind it), and `npm run
build` are all clean.

**Not built**: the Kanban board itself — lists, cards, drag-and-drop.
Clicking into a board lands on a real, linked, but placeholder page
(`features/boards/BoardDetailPage.tsx`) rather than a 404, since a dead
link would be worse than an honest "not built yet" — but no list/card UI,
dnd-kit, or STOMP client exists yet. Also not built: workspace member
management, and anything for comments/activity/labels beyond what the
board list page needed.

---

## Not built yet

This is a working skeleton, not a finished product. The notable gaps:

- **No workspace membership management.** Creating a workspace makes the
  caller its `OWNER` (`WorkspaceService#createWorkspace`), but there's no
  endpoint to invite another member, change someone's role, or remove them
  — `WorkspaceMemberRepository` exists and is read constantly (every
  `@PreAuthorize` check), but nothing writes to it after that first row.
- **Comments, activity feed, and card assignees aren't exposed.** All three
  entities (`Comment`, `ActivityEvent`, `CardAssignee`) and their schema
  exist; nothing populates `ActivityEvent` yet, and neither of the other two
  has a controller. Labels were the one collection-on-a-card relationship
  this task asked for; these three are the natural next ones.
- **No board events are published, and nothing subscribes to any yet.** The
  backend's STOMP broker (`WebSocketConfig`) is configured but unused —
  no endpoint broadcasts to `/topic/boards/{boardKey}` on a card move or
  any other change, and the frontend has no STOMP client at all (dropped
  along with the rest of the pre-redesign Kanban board UI — see
  [Frontend](#frontend) for what replaced it and what's still missing).
- **No access-token revocation.** Logout kills the *refresh* session — the
  still-live access token (up to 15 min old) keeps working until it expires
  naturally, the standard trade-off for stateless JWTs. A `jti` denylist in
  Redis would close that window if it's ever needed.
