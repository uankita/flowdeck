# Flowdeck developer tasks.
#
# Compose is always invoked with an explicit --env-file so the root .env is
# used for ${...} interpolation even though the compose file lives in docker/.

COMPOSE := docker compose --env-file .env -f docker/docker-compose.yml

# Testcontainers talks to the daemon directly and does not read Docker
# contexts, so it assumes /var/run/docker.sock. Under colima, rootless Docker
# or a Docker Desktop without the compatibility symlink, that path is absent.
# Resolve the real endpoint from the active context and hand it over.
# SOCKET_OVERRIDE is the path *inside* the VM, which is always the default one.
DOCKER_ENDPOINT := $(shell docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null)
TESTCONTAINERS_ENV := \
	DOCKER_HOST="$(DOCKER_ENDPOINT)" \
	TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

.DEFAULT_GOAL := help
.PHONY: help env up down restart logs ps psql redis-cli backend frontend test test-backend test-frontend build clean nuke

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

env: ## Create .env from the template if it does not exist
	@test -f .env || (cp .env.example .env && echo "Created .env from .env.example")

up: env ## Start Postgres + Redis and wait for health
	$(COMPOSE) up -d --wait

down: ## Stop containers (volumes are preserved)
	$(COMPOSE) down

restart: down up ## Restart the infrastructure

logs: ## Tail container logs
	$(COMPOSE) logs -f

ps: ## Show container status
	$(COMPOSE) ps

psql: ## Open a psql shell on the Postgres container
	$(COMPOSE) exec postgres psql -U $${POSTGRES_USER:-flowdeck} -d $${POSTGRES_DB:-flowdeck}

redis-cli: ## Open a redis-cli shell on the Redis container
	$(COMPOSE) exec redis redis-cli -a $${REDIS_PASSWORD:-flowdeck_dev_only} --no-auth-warning

backend: ## Run the Spring Boot API on :8080 (profile: local)
	cd backend && mvn spring-boot:run

frontend: ## Run the Vite dev server on :5173
	cd frontend && npm run dev

test: test-backend test-frontend ## Run all tests

test-backend: ## Run backend tests (spins up Postgres via Testcontainers)
	cd backend && $(TESTCONTAINERS_ENV) mvn test

test-frontend: ## Lint, typecheck, and build the frontend
	cd frontend && npm run lint && npm run build

build: ## Build both applications
	cd backend && mvn -DskipTests package
	cd frontend && npm run build

clean: ## Remove build output
	cd backend && mvn clean
	rm -rf frontend/dist frontend/node_modules/.tmp

nuke: ## Stop containers AND delete their volumes (destroys local data)
	$(COMPOSE) down -v
