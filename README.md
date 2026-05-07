# ShopFlow — E-Commerce Microservices Platform

Java 17 · Spring Boot 3.2 · Spring Cloud · Apache Kafka · Docker

## Architecture

```
Client → API Gateway (8080) → User Service    (8081) → MySQL :3307
                            → Order Service   (8082) → MySQL :3308
                            → Notification    (8083) → MySQL :3309

Order Service ──[Kafka: order-events]──► Notification Service

All services register with Eureka Server (8761)
```

## Services

| Service              | Port | Responsibility                          |
|----------------------|------|-----------------------------------------|
| eureka-server        | 8761 | Service discovery & registry            |
| api-gateway          | 8080 | JWT auth, routing via Eureka (lb://)    |
| user-service         | 8081 | Registration, login, JWT issuance       |
| order-service        | 8082 | Order CRUD, Kafka event publishing      |
| notification-service | 8083 | Kafka consumer, notification history    |

## Quick Start

```bash
# 1. Build all services
for svc in eureka-server api-gateway user-service order-service notification-service; do
  cd $svc && mvn package -DskipTests && cd ..
done

# 2. Start everything
docker-compose up --build

# 3. Wait ~60s for all services to register with Eureka
# Eureka dashboard: http://localhost:8761
```

## API Flow

```bash
# Register
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@shop.com","password":"secret123"}'

# Login → get token
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"secret123"}'

# Place order (use token from above)
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"productName":"MacBook Pro","quantity":1,"totalPrice":2499.99}'

# Check notifications (Kafka event consumed automatically)
curl http://localhost:8080/api/notifications/user/1 \
  -H "Authorization: Bearer <TOKEN>"
```

## Swagger UI

- User Service:         http://localhost:8081/swagger-ui.html
- Order Service:        http://localhost:8082/swagger-ui.html
- Notification Service: http://localhost:8083/swagger-ui.html

## Running Tests

```bash
cd user-service && mvn test          # Integration tests with H2
cd order-service && mvn test         # EmbeddedKafka producer test
cd notification-service && mvn test  # EmbeddedKafka consumer test
```

## JWT Secret

The default secret in application.yml is for development only.
Replace it in production with a strong 256-bit Base64-encoded key:
```bash
openssl rand -base64 32
```

## Tech Stack

- **Spring Boot 3.2** — all microservices
- **Spring Cloud Gateway** — reactive API gateway with global JWT filter
- **Spring Cloud Netflix Eureka** — service registry and client-side load balancing
- **Apache Kafka** — async order-events topic (order-service → notification-service)
- **Spring Security** — stateless JWT-based auth in user-service
- **Spring Data JPA + MySQL** — one database per service (data isolation)
- **Docker Compose** — single-command local setup
- **JUnit 5 + EmbeddedKafka** — integration tests without external dependencies
