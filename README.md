# ShopFlow — Production-Grade E-Commerce Microservices Platform

[![CI](https://github.com/VldDmv/shopflow/actions/workflows/ci.yml/badge.svg)](https://github.com/VldDmv/shopflow/actions/workflows/ci.yml)

Java 17 · Spring Boot 3.2 · Spring Cloud · Apache Kafka · PostgreSQL · Liquibase · Docker · Prometheus · Grafana

A reference event-driven microservices platform demonstrating production-grade patterns end to end: service discovery, API gateway, JWT auth, distributed transactions over Kafka, declarative inter-service HTTP with Feign + Resilience4j circuit breaker, schema migrations via Liquibase, and full observability with Actuator + Prometheus + Grafana.

---

## Architecture

```
                                    ┌─────────────────────────┐
                                    │  Eureka Server (8761)   │  service registry
                                    └────────────┬────────────┘
                                                 │
                  ┌──────────────────────────────┼──────────────────────────────┐
                  │                              │                              │
        ┌─────────▼─────────┐         ┌──────────▼─────────┐         ┌──────────▼──────────┐
Client →│ API Gateway 8080  │ ──lb──► │  User Service 8081 │         │  Order Service 8082 │
        │ (reactive, JWT)   │         │  Postgres :5433    │ ◄─Feign─┤  Postgres :5434     │
        └─────────┬─────────┘         └────────────────────┘         └──────────┬──────────┘
                  │                                                             │
                  │ ──lb──►                                                     │
                  │                                                             │ Kafka
                  │                                                  order-events
                  │                                                             │
                  │                  ┌──────────────────────────┐               │
                  └──lb─────────────►│ Notification Service 8083│ ◄─────────────┘
                                     │ Postgres :5435           │
                                     └──────────────────────────┘

Observability:  Prometheus (9090) ── scrape ──► Actuator on every service
                Grafana (3000)    ── query  ──► Prometheus
```

## Services

| Service              | Port | Responsibility                                                    |
|----------------------|-----:|-------------------------------------------------------------------|
| eureka-server        | 8761 | Service discovery & registry                                       |
| api-gateway          | 8080 | Reactive gateway, global JWT auth filter, routing via Eureka       |
| user-service         | 8081 | Registration, login, JWT issuance                                  |
| order-service        | 8082 | Orders, Kafka producer; verifies user via Feign + circuit breaker  |
| notification-service | 8083 | Kafka consumer, persists notifications, exposes history API        |
| prometheus           | 9090 | Scrapes `/actuator/prometheus` from each service                   |
| grafana              | 3000 | Pre-provisioned dashboard with JVM, HTTP, and circuit-breaker metrics |

---

## Tech Stack

### Core
- **Spring Boot 3.2**, Java 17 (Lombok)
- **Spring Cloud Gateway** — reactive API gateway with global JWT filter
- **Spring Cloud Netflix Eureka** — service registry & client-side load balancing
- **Spring Cloud OpenFeign** — declarative HTTP client between order-service and user-service
- **Resilience4j** — circuit breaker, retry, and time-limiter on Feign calls
- **Apache Kafka** — async `order-events` topic (order-service → notification-service)
- **Spring Security** — stateless JWT auth in user-service, BCrypt hashing
- **Spring Data JPA + Hibernate**

### Persistence & Migrations
- **PostgreSQL 16** — one database per service (data isolation)
- **Liquibase** — versioned, declarative schema migrations (YAML changelog)
- **HikariCP** connection pooling (Spring Boot default)

### Observability
- **Spring Boot Actuator** — health, metrics, info endpoints
- **Micrometer + Prometheus** registry — application, JVM, HTTP, circuit-breaker metrics
- **Prometheus** — scrapes all services, 15s interval
- **Grafana** — auto-provisioned dashboard (`monitoring/grafana/dashboards/shopflow-overview.json`)

### Testing
- **JUnit 5 + Mockito + AssertJ** — unit tests
- **Testcontainers** (PostgreSQL) + Spring Boot 3 `@ServiceConnection` — integration tests against real Postgres
- **EmbeddedKafka** — producer/consumer tests without external broker
- **MockBean** — Feign client stubbed in integration tests to keep them hermetic

### Tooling
- **MapStruct** — compile-time Entity ↔ DTO mapping
- **Springdoc OpenAPI** — Swagger UI on every service
- **Docker Compose** — one-command local setup with healthchecks
- **GitHub Actions** — matrix CI: build + test each service in parallel

---

## Quick Start

```bash
docker-compose up --build
# Wait ~60s for all services to register with Eureka and run Liquibase migrations
```

| What                  | URL                                              |
|-----------------------|--------------------------------------------------|
| Eureka dashboard      | http://localhost:8761                            |
| User Swagger UI       | http://localhost:8081/swagger-ui.html            |
| Order Swagger UI      | http://localhost:8082/swagger-ui.html            |
| Notification Swagger  | http://localhost:8083/swagger-ui.html            |
| Prometheus            | http://localhost:9090                            |
| Grafana (admin/admin) | http://localhost:3000                            |
| Actuator (any svc)    | http://localhost:808[1–3]/actuator               |

## API Flow

```bash
# Register
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@shop.com","password":"secret123"}'

# Login → get token
TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"secret123"}' | jq -r .token)

# Place order — order-service verifies user via Feign before persisting
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"productName":"MacBook Pro","quantity":1,"totalPrice":2499.99}'

# Notification was produced via Kafka and consumed asynchronously
curl http://localhost:8080/api/notifications/user/1 \
  -H "Authorization: Bearer $TOKEN"
```

## Resilience Demo

Stop user-service while keeping order-service running:

```bash
docker-compose stop user-service
# Place 11+ orders. After ~5 failures the circuit opens.
# Order requests still succeed (degraded fallback) and are logged with WARN.
```

Watch circuit state in real time:
- Grafana → "ShopFlow Overview" → "Circuit Breaker State" panel
- Or: `curl http://localhost:8082/actuator/circuitbreakers`

## Database Migrations

Schema is managed by Liquibase. Each service ships its own changelog:

```
{service}/src/main/resources/db/changelog/
├── db.changelog-master.yaml
└── changes/
    └── 001-create-{table}.yaml
```

Migrations run automatically on startup. To add a new change, create `002-...yaml` and include it in the master.

## Running Tests

```bash
# Per service
cd user-service && mvn verify         # Testcontainers Postgres + integration tests
cd order-service && mvn verify        # Testcontainers Postgres + EmbeddedKafka
cd notification-service && mvn verify # Testcontainers Postgres + EmbeddedKafka

# All services in CI: see .github/workflows/ci.yml (matrix build per service)
```

## JWT Secret

The default secret in `application.yml` is for development only.
Replace in production with a strong 256-bit Base64-encoded key:
```bash
openssl rand -base64 32
```

## Project Layout

```
shopflow/
├── eureka-server/             # service registry
├── api-gateway/               # reactive gateway, JWT filter
├── user-service/              # auth + JWT
├── order-service/             # orders + Kafka producer + Feign client
├── notification-service/      # Kafka consumer
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/
│       ├── provisioning/      # auto-loaded datasources & dashboards config
│       └── dashboards/        # JSON dashboards
├── .github/workflows/ci.yml   # matrix CI per service
├── docker-compose.yml
└── README.md
```